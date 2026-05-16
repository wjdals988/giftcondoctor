package com.giftcondoctor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel

@Composable
fun LoginScreen(sessionViewModel: SessionViewModel) {
    val context = LocalContext.current
    val busy by sessionViewModel.busy.collectAsStateWithLifecycle()
    val message by sessionViewModel.message.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("기프티콘닥터", style = MaterialTheme.typography.headlineLarge)
        Text("함께 쓰는 쿠폰방", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )

        InlineMessage(message)

        Button(
            onClick = { sessionViewModel.signIn(email.trim(), password) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Login, contentDescription = null)
            Text("로그인", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = { sessionViewModel.createAccount(email.trim(), password) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("이메일로 회원가입")
        }
        OutlinedButton(
            onClick = { sessionViewModel.signInWithGoogle(context) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Google로 계속하기")
        }

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
    }
}
