package maestro.drivers

import com.google.common.truth.Truth.assertThat
import maestro.TreeNode
import org.junit.jupiter.api.Test

class AndroidCustomCommandExtractorTest {

    @Test
    fun extractsCustomCommandsFromAccessibilityTextAndBodyText() {
        val root = TreeNode(
            children = listOf(
                TreeNode(
                    attributes = mutableMapOf(
                        "accessibilityText" to "MaestroCustomCommand: Login user",
                        "text" to "- tapOn: Email\n- inputText: \$email",
                    )
                ),
                TreeNode(
                    attributes = mutableMapOf(
                        "accessibilityText" to "NotACustomCommand",
                        "text" to "- tapOn: Ignore me",
                    )
                )
            )
        )

        val commands = AndroidCustomCommandExtractor.extract(root)

        assertThat(commands).hasSize(1)
        assertThat(commands[0].names).containsExactly("Login user")
        assertThat(commands[0].body).isEqualTo("- tapOn: Email\n- inputText: \$email")
    }

    @Test
    fun preservesLeadingIndentationAcrossBodyLines() {
        val root = TreeNode(
            children = listOf(
                TreeNode(
                    attributes = mutableMapOf(
                        "accessibilityText" to "MaestroCustomCommand: Hello",
                        "text" to "\n              - tapOn: hello\n              - waitForAnimationToEnd\n              - assertVisible: hello\n            ",
                    )
                )
            )
        )

        val commands = AndroidCustomCommandExtractor.extract(root)

        assertThat(commands).hasSize(1)
        assertThat(commands[0].body).isEqualTo(
            "              - tapOn: hello\n" +
                "              - waitForAnimationToEnd\n" +
                "              - assertVisible: hello"
        )
    }

    @Test
    fun ignoresCommandsWithoutNameOrBody() {
        val root = TreeNode(
            children = listOf(
                TreeNode(
                    attributes = mutableMapOf(
                        "accessibilityText" to "MaestroCustomCommand: ",
                        "text" to "- tapOn: Email",
                    )
                ),
                TreeNode(
                    attributes = mutableMapOf(
                        "accessibilityText" to "MaestroCustomCommand: Login user",
                        "text" to " ",
                    )
                )
            )
        )

        assertThat(AndroidCustomCommandExtractor.extract(root)).isEmpty()
    }
}
