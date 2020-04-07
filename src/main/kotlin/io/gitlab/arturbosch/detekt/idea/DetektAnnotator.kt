package io.gitlab.arturbosch.detekt.idea

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.TextLocation
import io.gitlab.arturbosch.detekt.idea.config.DetektConfigStorage
import io.gitlab.arturbosch.detekt.idea.util.isDetektEnabled
import io.gitlab.arturbosch.detekt.idea.util.showNotification
import io.gitlab.arturbosch.detekt.idea.util.toTmpFile

class DetektAnnotator : ExternalAnnotator<PsiFile, List<Finding>>() {

    override fun collectInformation(file: PsiFile): PsiFile = file

    override fun doAnnotate(collectedInfo: PsiFile): List<Finding> {
        if (!collectedInfo.project.isDetektEnabled()) {
            return emptyList()
        }
        val pathToAnalyze = collectedInfo.toTmpFile() ?: return emptyList()
        val service = ConfiguredService(collectedInfo.project)

        val problems = service.validate()
        if (problems.isNotEmpty()) {
            showNotification(problems, collectedInfo.project)
            return emptyList()
        }

        return service.execute(pathToAnalyze, autoCorrect = false)
    }

    override fun apply(
        file: PsiFile,
        annotationResult: List<Finding>,
        holder: AnnotationHolder
    ) {
        val configuration = DetektConfigStorage.instance(file.project)
        for (finding in annotationResult) {
            val textRange = finding.charPosition.toTextRange()
            val message = finding.id + ": " + finding.messageOrDescription()
            if (configuration.treatAsError) {
                holder.createErrorAnnotation(textRange, message)
            } else {
                holder.createWarningAnnotation(textRange, message)
            }
        }
    }

    private fun TextLocation.toTextRange(): TextRange = TextRange.create(start, end)
}
