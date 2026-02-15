package com.shadowai.app.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Manages conversation branching allowing users to fork chats at any point.
 *
 * Features:
 * - Branch from any message
 * - Track branch hierarchy
 * - Merge branches back to main
 * - Navigate between branches
 */
class ConversationBranchManager {

    data class Branch(
        val id: String = UUID.randomUUID().toString(),
        val conversationId: String,
        val branchPointMessageId: String,
        val parentId: String? = null,
        val name: String,
        val messages: List<ChatMessage> = emptyList(),
        val createdAt: Long = System.currentTimeMillis(),
        val isMerged: Boolean = false
    ) {
        val hasParent: Boolean
            get() = parentId != null
    }

    data class ConversationState(
        val currentBranchId: String,
        val mainBranchId: String,
        val branches: List<Branch>
    )

    private val _conversationState = MutableStateFlow<ConversationState?>(null)
    val conversationState: StateFlow<ConversationState?> = _conversationState

    private val branches = HashMap<String, Branch>()

    /**
     * Initialize branch manager with a main conversation.
     */
    fun initialize(conversationId: String, messages: List<ChatMessage>) {
        val mainBranch = Branch(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            branchPointMessageId = "",
            parentId = null,
            name = "Main",
            messages = messages
        )

        branches.clear()
        branches[mainBranch.id] = mainBranch

        _conversationState.value = ConversationState(
            currentBranchId = mainBranch.id,
            mainBranchId = mainBranch.id,
            branches = listOf(mainBranch)
        )

        log("Initialized branch manager with main branch ${mainBranch.id}")
    }

    /**
     * Create a new branch from a specific message.
     *
     * @param messageId The ID of the message to branch from
     * @param branchName Optional name for the new branch
     * @return The ID of the newly created branch, or null if failed
     */
    fun createBranch(messageId: String, branchName: String = "Branch"): String? {
        val currentState = _conversationState.value ?: return null
        val currentBranch = branches[currentState.currentBranchId] ?: return null

        // Find the message and all messages before it
        val branchPointIndex = currentBranch.messages.indexOfFirst { it.id == messageId }
        if (branchPointIndex == -1) {
            log("Message $messageId not found in current branch")
            return null
        }

        // Create the new branch with messages up to the branch point
        val branchMessages = currentBranch.messages.subList(0, branchPointIndex + 1)
        val newBranch = Branch(
            conversationId = currentBranch.conversationId,
            branchPointMessageId = messageId,
            parentId = currentBranch.id,
            name = branchName,
            messages = branchMessages
        )

        branches[newBranch.id] = newBranch

        // Update state
        _conversationState.value = currentState.copy(
            currentBranchId = newBranch.id,
            branches = branches.values.toList()
        )

        log("Created branch ${newBranch.id} from message $messageId")
        return newBranch.id
    }

    /**
     * Switch to a different branch.
     *
     * @param branchId The ID of the branch to switch to
     * @return true if successful, false otherwise
     */
    fun switchBranch(branchId: String): Boolean {
        val currentState = _conversationState.value ?: return false
        val targetBranch = branches[branchId] ?: return false

        _conversationState.value = currentState.copy(
            currentBranchId = branchId
        )

        log("Switched to branch $branchId (${targetBranch.name})")
        return true
    }

    /**
     * Get the current branch.
     */
    fun getCurrentBranch(): Branch? {
        val currentState = _conversationState.value ?: return null
        return branches[currentState.currentBranchId]
    }

    /**
     * Get all branches for the current conversation.
     */
    fun getAllBranches(): List<Branch> {
        val currentState = _conversationState.value ?: return emptyList()
        return currentState.branches
    }

    /**
     * Get a specific branch by ID.
     */
    fun getBranch(branchId: String): Branch? {
        return branches[branchId]
    }

    /**
     * Add a message to the current branch.
     */
    fun addMessage(message: ChatMessage) {
        val currentState = _conversationState.value ?: return
        val currentBranch = branches[currentState.currentBranchId] ?: return

        val updatedMessages = currentBranch.messages + message
        val updatedBranch = currentBranch.copy(messages = updatedMessages)
        branches[currentBranch.id] = updatedBranch

        _conversationState.value = currentState.copy(
            branches = branches.values.toList()
        )
    }

    /**
     * Merge the current branch into a target branch.
     *
     * @param targetBranchId The ID of the branch to merge into (typically main)
     * @return true if successful, false otherwise
     */
    fun mergeBranch(targetBranchId: String): Boolean {
        val currentState = _conversationState.value ?: return false
        val currentBranch = branches[currentState.currentBranchId] ?: return false
        val targetBranch = branches[targetBranchId] ?: return false

        if (currentBranch.id == targetBranch.id) {
            log("Cannot merge a branch into itself")
            return false
        }

        if (currentBranch.isMerged) {
            log("Branch ${currentBranch.id} is already merged")
            return false
        }

        // Check if current branch is a descendant of target
        if (!isDescendant(currentBranch, targetBranch)) {
            log("Cannot merge unrelated branches")
            return false
        }

        // Merge the unique messages from current branch into target
        val branchPointIndex = targetBranch.messages.indexOfFirst { it.id == currentBranch.branchPointMessageId }
        val messagesToMerge = if (branchPointIndex != -1 && branchPointIndex < currentBranch.messages.size) {
            currentBranch.messages.drop(branchPointIndex + 1)
        } else {
            currentBranch.messages
        }

        val mergedMessages = targetBranch.messages + messagesToMerge
        val updatedTargetBranch = targetBranch.copy(messages = mergedMessages)

        // Mark current branch as merged
        val updatedSourceBranch = currentBranch.copy(isMerged = true)

        branches[targetBranchId] = updatedTargetBranch
        branches[currentBranch.id] = updatedSourceBranch

        // Switch to the target branch after merge
        _conversationState.value = currentState.copy(
            currentBranchId = targetBranchId,
            branches = branches.values.toList()
        )

        log("Merged branch ${currentBranch.id} into $targetBranchId")
        return true
    }

    /**
     * Delete a branch (cannot delete the main branch).
     *
     * @param branchId The ID of the branch to delete
     * @return true if successful, false otherwise
     */
    fun deleteBranch(branchId: String): Boolean {
        val currentState = _conversationState.value ?: return false

        if (branchId == currentState.mainBranchId) {
            log("Cannot delete main branch")
            return false
        }

        if (branchId == currentState.currentBranchId) {
            log("Cannot delete current branch")
            return false
        }

        branches.remove(branchId)

        _conversationState.value = currentState.copy(
            branches = branches.values.toList()
        )

        log("Deleted branch $branchId")
        return true
    }

    /**
     * Rename a branch.
     */
    fun renameBranch(branchId: String, newName: String): Boolean {
        val branch = branches[branchId] ?: return false

        branches[branchId] = branch.copy(name = newName)

        val currentState = _conversationState.value ?: return false
        _conversationState.value = currentState.copy(
            branches = branches.values.toList()
        )

        log("Renamed branch $branchId to $newName")
        return true
    }

    /**
     * Get the branch hierarchy as a tree structure.
     */
    fun getBranchHierarchy(): List<BranchNode> {
        val currentState = _conversationState.value ?: return emptyList()
        val mainBranch = branches[currentState.mainBranchId] ?: return emptyList()

        return buildHierarchy(mainBranch)
    }

    private fun buildHierarchy(branch: Branch): List<BranchNode> {
        val children = branches.values.filter { it.parentId == branch.id }
        return listOf(
            BranchNode(
                branch = branch,
                children = children.map { buildHierarchy(it) }.flatten()
            )
        )
    }

    /**
     * Check if a branch is a descendant of another branch.
     */
    private fun isDescendant(possibleDescendant: Branch, possibleAncestor: Branch): Boolean {
        var current = possibleDescendant
        while (current.parentId != null) {
            if (current.parentId == possibleAncestor.id) {
                return true
            }
            current = branches[current.parentId] ?: return false
        }
        return false
    }

    /**
     * Clear all branches and reset state.
     */
    fun clear() {
        branches.clear()
        _conversationState.value = null
        log("Cleared all branches")
    }

    private fun log(message: String) {
        android.util.Log.d("ConversationBranchManager", message)
    }
}

/**
 * Represents a node in the branch hierarchy tree.
 */
data class BranchNode(
    val branch: ConversationBranchManager.Branch,
    val children: List<BranchNode>
) {
    val isMain: Boolean
        get() = branch.parentId == null

    val isLeaf: Boolean
        get() = children.isEmpty()

    val depth: Int
        get() = calculateDepth(this)

    private fun calculateDepth(node: BranchNode): Int {
        if (node.children.isEmpty()) return 1
        return 1 + node.children.maxOf { calculateDepth(it) }
    }
}

/**
 * UI component to display branch selection dialog.
 */
data class BranchUIState(
    val isBranching: Boolean = false,
    val branches: List<ConversationBranchManager.Branch> = emptyList(),
    val currentBranch: ConversationBranchManager.Branch? = null,
    val showBranchDialog: Boolean = false,
    val showMergeDialog: Boolean = false,
    val selectedBranchId: String? = null
)