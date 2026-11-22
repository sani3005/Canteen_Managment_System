package com.canteen.student.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.canteen.student.data.api.SessionManager
import com.canteen.student.data.model.CanteenCodeUtils
import com.canteen.student.data.repository.CanteenRepositoryImpl
import com.canteen.student.data.repository.StudentRepositoryImpl
import kotlinx.coroutines.launch

enum class UserRole {
    STUDENT,
    OWNER
}

enum class AuthMode {
    LOGIN,
    SIGN_UP
}

private fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isBlank()) return false
    val atIndex = trimmed.indexOf('@')
    val dotIndex = trimmed.lastIndexOf('.')
    return atIndex > 0 && dotIndex > atIndex + 1 && dotIndex < trimmed.length - 1 && !trimmed.contains(" ")
}


@Composable
fun AuthScreen(
    onLoginSuccess: (UserRole) -> Unit
) {
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var canteenName by remember { mutableStateOf("") }
    var canteenLocation by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val studentRepo = remember { StudentRepositoryImpl() }
    val canteenRepo = remember { CanteenRepositoryImpl() }

    fun handleAuthSubmit() {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        val trimmedFullName = fullName.trim()
        val trimmedCanteenName = canteenName.trim()
        val trimmedLocation = canteenLocation.trim()
        val trimmedJoinCode = joinCode.trim().uppercase()

        errorMessage = null

        if (selectedRole == UserRole.STUDENT) {
            if (!isValidEmail(trimmedEmail)) {
                errorMessage = "Please enter a valid email address (e.g. user@example.com)."
                return
            }

            if (trimmedPassword.length < 4) {
                errorMessage = "Please enter a password with at least 4 characters."
                return
            }

            if (authMode == AuthMode.SIGN_UP) {
                if (trimmedFullName.isBlank()) {
                    errorMessage = "Please enter your full name."
                    return
                }
                if (trimmedJoinCode.isBlank() || !CanteenCodeUtils.isValidCode(trimmedJoinCode)) {
                    errorMessage = "Please enter a valid 6-character canteen join code."
                    return
                }

                isLoading = true
                scope.launch {
                    studentRepo.registerStudent(
                        fullName = trimmedFullName,
                        email = trimmedEmail,
                        joinCode = trimmedJoinCode,
                        password = trimmedPassword,
                        phone = phone.trim().takeIf { it.isNotBlank() }
                    ).onSuccess { registerData ->
                        isLoading = false
                        SessionManager.setStudentSession(
                            token = registerData.token,
                            sId = registerData.studentId,
                            cId = registerData.canteenId,
                            name = trimmedFullName,
                            email = trimmedEmail
                        )
                        onLoginSuccess(UserRole.STUDENT)
                    }.onFailure { throwable ->
                        isLoading = false
                        val errorMsg = throwable.localizedMessage ?: "Student registration failed"
                        errorMessage = if (errorMsg.contains("already registered", ignoreCase = true) || errorMsg.contains("409")) {
                            "This email is already registered for this canteen. Please switch to 'Log In' to sign in."
                        } else if (errorMsg.contains("join_code", ignoreCase = true) || errorMsg.contains("Invalid canteen")) {
                            "Invalid 6-character canteen join code. Please ask your canteen owner for the correct code."
                        } else {
                            errorMsg
                        }
                    }
                }
            } else {
                // STUDENT LOGIN
                isLoading = true
                scope.launch {
                    studentRepo.loginStudent(trimmedEmail, trimmedPassword)
                        .onSuccess { loginData ->
                            isLoading = false
                            SessionManager.setStudentSession(
                                token = loginData.token,
                                sId = loginData.studentId,
                                cId = loginData.canteenId,
                                name = loginData.fullName,
                                email = trimmedEmail
                            )
                            onLoginSuccess(UserRole.STUDENT)
                        }
                        .onFailure { throwable ->
                            isLoading = false
                            SessionManager.clearSession()
                            val primaryMsg = throwable.localizedMessage ?: ""
                            val isUnauthorized = primaryMsg.contains("Forbidden", ignoreCase = true) ||
                                    primaryMsg.contains("token", ignoreCase = true) ||
                                    primaryMsg.contains("403") || primaryMsg.contains("401")

                            errorMessage = if (isUnauthorized) {
                                "Student account not found or invalid credentials. If you haven't registered yet, please tap 'Sign Up'."
                            } else {
                                primaryMsg.ifBlank { "Unable to connect to canteen server. Please check your network or server status." }
                            }
                        }
                }
            }
        } else {
            // OWNER ROLE
            if (trimmedPassword.isBlank()) {
                errorMessage = "Please enter your password."
                return
            }

            if (authMode == AuthMode.SIGN_UP) {
                if (trimmedFullName.isBlank()) {
                    errorMessage = "Please enter your owner full name."
                    return
                }
                if (trimmedCanteenName.isBlank()) {
                    errorMessage = "Please enter your canteen name."
                    return
                }
                if (trimmedLocation.isBlank()) {
                    errorMessage = "Please enter your canteen location."
                    return
                }

                isLoading = true
                scope.launch {
                    canteenRepo.registerOwner(
                        fullName = trimmedFullName,
                        email = trimmedEmail,
                        password = trimmedPassword,
                        canteenName = trimmedCanteenName,
                        location = trimmedLocation,
                        photoUrl = null
                    ).onSuccess { regData ->
                        isLoading = false
                        SessionManager.setOwnerSession(
                            token = regData.token,
                            cName = trimmedCanteenName,
                            oName = trimmedFullName,
                            loc = trimmedLocation,
                            code = regData.joinCode
                        )
                        onLoginSuccess(UserRole.OWNER)
                    }.onFailure { throwable ->
                        isLoading = false
                        SessionManager.clearSession()
                        val errMsg = throwable.localizedMessage ?: "Owner registration failed."
                        errorMessage = if (errMsg.contains("already registered", ignoreCase = true) || errMsg.contains("409")) {
                            "This email is already registered as an owner. Please switch to 'Log In'."
                        } else {
                            errMsg
                        }
                    }
                }
            } else {
                // OWNER LOGIN
                isLoading = true
                scope.launch {
                    canteenRepo.loginOwner(
                        email = trimmedEmail,
                        password = trimmedPassword
                    ).onSuccess { loginData ->
                        isLoading = false
                        SessionManager.setOwnerSession(
                            token = loginData.token,
                            cName = loginData.name,
                            oName = loginData.ownerName,
                            loc = loginData.location,
                            code = loginData.joinCode
                        )
                        onLoginSuccess(UserRole.OWNER)
                    }.onFailure { throwable ->
                        isLoading = false
                        SessionManager.clearSession()
                        val errStr = throwable.localizedMessage ?: ""
                        val isUnauthorized = errStr.contains("Forbidden", ignoreCase = true) ||
                                errStr.contains("token", ignoreCase = true) ||
                                errStr.contains("403") || errStr.contains("401")

                        errorMessage = if (isUnauthorized) {
                            "Invalid email or password for canteen owner account."
                        } else {
                            errStr.ifBlank { "Unable to connect to server. Please check your network or server status." }
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Namaste 🙏",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 50.dp)
            )

            Text(
                text = "Select User Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleCard(
                    title = "Student / Faculty",
                    icon = Icons.Default.AccountBox,
                    isSelected = selectedRole == UserRole.STUDENT,
                    onClick = {
                        selectedRole = UserRole.STUDENT
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                )

                RoleCard(
                    title = "Canteen Owner",
                    icon = Icons.Default.Home,
                    isSelected = selectedRole == UserRole.OWNER,
                    onClick = {
                        selectedRole = UserRole.OWNER
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (authMode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(if (selectedRole == UserRole.STUDENT) "Full Name *" else "Owner Full Name *") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedRole == UserRole.OWNER) {
                            OutlinedTextField(
                                value = canteenName,
                                onValueChange = { canteenName = it },
                                label = { Text("Canteen Name *") },
                                leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = canteenLocation,
                                onValueChange = { canteenLocation = it },
                                label = { Text("Canteen Location *") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (selectedRole == UserRole.STUDENT) "Email Address *" else "Owner Email *") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password *") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (authMode == AuthMode.SIGN_UP) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedRole == UserRole.STUDENT) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it },
                                label = { Text("Canteen Join Code * (6-character code)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    AnimatedVisibility(visible = errorMessage != null) {
                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { handleAuthSubmit() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (authMode == AuthMode.LOGIN) "Log In" else "Sign Up",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            authMode = if (authMode == AuthMode.LOGIN) AuthMode.SIGN_UP else AuthMode.LOGIN
                            errorMessage = null
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = if (authMode == AuthMode.LOGIN)
                                "Don't have an account? Sign Up"
                            else
                                "Already have an account? Log In"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .height(32.dp)
                    .width(32.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
