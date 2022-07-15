package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.ActiveByDefault
import io.gitlab.arturbosch.detekt.api.internal.Configuration
import io.gitlab.arturbosch.detekt.rules.lastArgumentMatchesUrl
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * This rule reports lines of code which exceed a defined maximum line length.
 *
 * Long lines might be hard to read on smaller screens or printouts. Additionally, having a maximum line length
 * in the codebase will help make the code more uniform.
 */
@ActiveByDefault(since = "1.0.0")
class MaxLineLength(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Line detected, which is longer than the defined maximum line length in the code style.",
        Debt.FIVE_MINS
    )

    @Suppress("MemberNameEqualsClassName")
    @Configuration("maximum line length")
    private val maxLineLength: Int by config(DEFAULT_IDEA_LINE_LENGTH)

    @Configuration("if package statements should be ignored")
    private val excludePackageStatements: Boolean by config(true)

    @Configuration("if import statements should be ignored")
    private val excludeImportStatements: Boolean by config(true)

    @Configuration("if comment statements should be ignored")
    private val excludeCommentStatements: Boolean by config(false)

    @Suppress("UnconditionalJumpStatementInLoop")
    fun visit(element: KtFileContent) {
        var offset = 0
        val lines = element.content.toMutableList()
        val file = element.file

        lines.forEachIndexed { index, line ->
            offset += line.length
            if (!isValidLine(line)) {
                if (containsRawString(line)) {
                    var pointer = index + 1
                    while (!containsRawString(lines[pointer])) {
                        lines.removeAt(pointer)
                        pointer += 1
                        return
                    }
                } else {
                    val ktElement = findFirstMeaningfulKtElementInParents(file, offset, line)
                    if (ktElement != null) {
                        report(CodeSmell(issue, Entity.from(ktElement), issue.description))
                    } else {
                        report(CodeSmell(issue, Entity.from(file, offset), issue.description))
                    }
                }
            }

            offset += 1 /* '\n' */
        }
    }
    private fun containsRawString(line: String): Boolean = line.contains(TQ)

    private fun isValidLine(line: String): Boolean {
        val isUrl = line.lastArgumentMatchesUrl()
        return line.length <= maxLineLength || isIgnoredStatement(line) || isUrl
    }

    private fun isIgnoredStatement(line: String): Boolean {
        return containsIgnoredPackageStatement(line) ||
            containsIgnoredImportStatement(line) ||
            containsIgnoredCommentStatement(line)
    }

    private fun containsIgnoredPackageStatement(line: String): Boolean {
        if (!excludePackageStatements) return false

        return line.trimStart().startsWith("package ")
    }

    private fun containsIgnoredImportStatement(line: String): Boolean {
        if (!excludeImportStatements) return false

        return line.trimStart().startsWith("import ")
    }

    private fun containsIgnoredCommentStatement(line: String): Boolean {
        if (!excludeCommentStatements) return false

        return line.trimStart().startsWith("//") ||
            line.trimStart().startsWith("/*") ||
            line.trimStart().startsWith("*")
    }

    companion object {
        private const val DEFAULT_IDEA_LINE_LENGTH = 120
        private val BLANK_OR_QUOTES = """[\s"]*""".toRegex()
        private const val TQ = "\"\"\""

        private fun findFirstMeaningfulKtElementInParents(file: KtFile, offset: Int, line: String): PsiElement? {
            return findKtElementInParents(file, offset, line)
                .firstOrNull { !BLANK_OR_QUOTES.matches(it.text) }
        }
    }
}
