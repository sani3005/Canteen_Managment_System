package com.canteen.student.data.api

import android.content.Context
import android.content.SharedPreferences
import com.canteen.student.ui.auth.UserRole
import java.security.MessageDigest

/**
 * Singleton to manage current user session and authentication tokens for HTTP requests.
 */
object SessionManager {

    private const val PREFS_NAME = "canteen_session_prefs"

    private const val KEY_ROLE = "key_role"
    private const val KEY_STUDENT_TOKEN = "key_student_token"
    private const val KEY_OWNER_TOKEN = "key_owner_token"
    private const val KEY_STUDENT_ID = "key_student_id"
    private const val KEY_CANTEEN_ID = "key_canteen_id"
    private const val KEY_STUDENT_NAME = "key_student_name"
    private const val KEY_STUDENT_EMAIL = "key_student_email"
    private const val KEY_CANTEEN_NAME = "key_canteen_name"
    private const val KEY_OWNER_NAME = "key_owner_name"
    private const val KEY_CANTEEN_LOCATION = "key_canteen_location"
    private const val KEY_CANTEEN_PHOTO_URL = "key_canteen_photo_url"
    private const val KEY_CANTEEN_JOIN_CODE = "key_canteen_join_code"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromPrefs()
        }
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val roleStr = p.getString(KEY_ROLE, null)
        if (roleStr != null) {
            currentRole = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.STUDENT)
        }
        studentToken = p.getString(KEY_STUDENT_TOKEN, null)
        ownerToken = p.getString(KEY_OWNER_TOKEN, null)
        studentId = if (p.contains(KEY_STUDENT_ID)) p.getInt(KEY_STUDENT_ID, -1).takeIf { it != -1 } else null
        canteenId = if (p.contains(KEY_CANTEEN_ID)) p.getInt(KEY_CANTEEN_ID, -1).takeIf { it != -1 } else null
        studentName = p.getString(KEY_STUDENT_NAME, null)
        studentEmail = p.getString(KEY_STUDENT_EMAIL, null)
        canteenName = p.getString(KEY_CANTEEN_NAME, null)
        ownerName = p.getString(KEY_OWNER_NAME, null)
        canteenLocation = p.getString(KEY_CANTEEN_LOCATION, null)
        canteenPhotoUrl = p.getString(KEY_CANTEEN_PHOTO_URL, null)
        canteenJoinCode = p.getString(KEY_CANTEEN_JOIN_CODE, null)
    }

    private fun saveToPrefs() {
        val p = prefs?.edit() ?: return
        p.putString(KEY_ROLE, currentRole.name)
        p.putString(KEY_STUDENT_TOKEN, studentToken)
        p.putString(KEY_OWNER_TOKEN, ownerToken)
        studentId?.let { p.putInt(KEY_STUDENT_ID, it) } ?: p.remove(KEY_STUDENT_ID)
        canteenId?.let { p.putInt(KEY_CANTEEN_ID, it) } ?: p.remove(KEY_CANTEEN_ID)
        p.putString(KEY_STUDENT_NAME, studentName)
        p.putString(KEY_STUDENT_EMAIL, studentEmail)
        p.putString(KEY_CANTEEN_NAME, canteenName)
        p.putString(KEY_OWNER_NAME, ownerName)
        p.putString(KEY_CANTEEN_LOCATION, canteenLocation)
        p.putString(KEY_CANTEEN_PHOTO_URL, canteenPhotoUrl)
        p.putString(KEY_CANTEEN_JOIN_CODE, canteenJoinCode)
        p.apply()
    }

    var currentRole: UserRole = UserRole.STUDENT
    var studentToken: String? = null
    var ownerToken: String? = null

    var studentId: Int? = null
    var canteenId: Int? = null
    var studentName: String? = null
    var studentEmail: String? = null

    // Canteen owner profile details
    var canteenName: String? = null
    var ownerName: String? = null
    var canteenLocation: String? = null
    var canteenPhotoUrl: String? = null
    var canteenJoinCode: String? = null

    val isLoggedIn: Boolean
        get() = when (currentRole) {
            UserRole.STUDENT -> !studentToken.isNullOrBlank()
            UserRole.OWNER -> !ownerToken.isNullOrBlank()
        }

    val activeToken: String?
        get() = when (currentRole) {
            UserRole.STUDENT -> studentToken.takeUnless { it.isNullOrBlank() }
            UserRole.OWNER -> ownerToken.takeUnless { it.isNullOrBlank() }
        }

    fun generate64HexToken(email: String, secret: String): String {
        val input = "${email.trim().lowercase()}:$secret"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun setStudentSession(
        token: String,
        sId: Int? = null,
        cId: Int? = null,
        name: String? = null,
        email: String? = null
    ) {
        currentRole = UserRole.STUDENT
        studentToken = token.trim()
        if (sId != null) studentId = sId
        if (cId != null) canteenId = cId
        if (!name.isNullOrBlank()) studentName = name
        if (!email.isNullOrBlank()) studentEmail = email
        saveToPrefs()
    }

    fun setOwnerSession(
        token: String,
        cName: String? = null,
        oName: String? = null,
        loc: String? = null,
        code: String? = null
    ) {
        currentRole = UserRole.OWNER
        ownerToken = token.trim()
        if (!cName.isNullOrBlank()) canteenName = cName
        if (!oName.isNullOrBlank()) ownerName = oName
        if (!loc.isNullOrBlank()) canteenLocation = loc
        if (!code.isNullOrBlank()) canteenJoinCode = code
        saveToPrefs()
    }

    fun clearSession() {
        studentToken = null
        ownerToken = null
        studentId = null
        canteenId = null
        studentName = null
        studentEmail = null
        canteenName = null
        ownerName = null
        canteenLocation = null
        canteenPhotoUrl = null
        canteenJoinCode = null
        prefs?.edit()?.clear()?.apply()
    }
}
