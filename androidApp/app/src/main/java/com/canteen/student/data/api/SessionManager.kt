package com.canteen.student.data.api

import com.canteen.student.ui.auth.UserRole

/**
 * Singleton to manage current user session and authentication tokens for HTTP requests.
 */
object SessionManager {

    const val DEFAULT_OWNER_TOKEN = "owner_secret_token_123"
    const val DEFAULT_STUDENT_TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    var currentRole: UserRole = UserRole.STUDENT
    var studentToken: String? = DEFAULT_STUDENT_TOKEN
    var ownerToken: String = DEFAULT_OWNER_TOKEN

    var studentId: Int? = null
    var canteenId: Int? = null
    var studentName: String? = null
    var studentEmail: String? = null

    val activeToken: String?
        get() = when (currentRole) {
            UserRole.STUDENT -> studentToken.takeUnless { it.isNull_or_blank() } ?: DEFAULT_STUDENT_TOKEN
            UserRole.OWNER -> ownerToken.takeUnless { it.isNull_or_blank() } ?: DEFAULT_OWNER_TOKEN
        }

    fun setStudentSession(token: String, sId: Int? = null, cId: Int? = null, name: String? = null, email: String? = null) {
        currentRole = UserRole.STUDENT
        studentToken = token
        studentId = sId
        canteenId = cId
        studentName = name
        studentEmail = email
    }

    fun setOwnerSession(token: String = DEFAULT_OWNER_TOKEN) {
        currentRole = UserRole.OWNER
        ownerToken = token
    }

    fun clearSession() {
        studentToken = null
        studentId = null
        canteenId = null
        studentName = null
        studentEmail = null
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
