package tedwester.convo.features.chat.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ChatState.createProjectImpl(name: String, description: String) {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return
    if (projectNameExists(trimmedName)) return
    scope.launch {
        withContext(Dispatchers.IO) {
            repository.createProject(trimmedName, description)
        }
        projects = withContext(Dispatchers.IO) { repository.listProjects() }
    }
}

internal fun ChatState.deleteProjectImpl(projectId: String) {
    scope.launch {
        withContext(Dispatchers.IO) { repository.deleteProject(projectId) }
        projects = withContext(Dispatchers.IO) { repository.listProjects() }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }
}

internal fun ChatState.projectNameExistsImpl(name: String): Boolean =
    projects.any { it.name.equals(name.trim(), ignoreCase = true) }

internal fun ChatState.assignChatToProjectImpl(chatId: String, projectId: String?) {
    scope.launch {
        withContext(Dispatchers.IO) {
            repository.setChatProject(chatId, projectId)
        }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }
}
