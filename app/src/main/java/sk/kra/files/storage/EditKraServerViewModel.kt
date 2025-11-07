package sk.kra.files.storage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.kra.files.provider.kra.client.KraApiClient
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.provider.kra.client.PasswordAuthentication
import sk.kra.files.util.ActionState
import sk.kra.files.util.isReady

class EditKraServerViewModel : ViewModel() {
    private val _connectState =
        MutableStateFlow<ActionState<KraServer, Unit>>(ActionState.Ready())
    val connectState = _connectState.asStateFlow()

    fun connect(
        authority: KraAuthority,
        authentication: PasswordAuthentication,
        customName: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            check(_connectState.value.isReady)

            // Create server object
            val server = KraServer(
                id = null,
                customName = customName,
                authority = authority,
                authentication = authentication
            )

            _connectState.value = ActionState.Running(server)
            _connectState.value = try {
                val client = KraApiClient(authority, authentication)
                // Test connection by getting user info
                val userInfo = client.getUserInfo()

                // Connection successful
                ActionState.Success(server, Unit)
            } catch (e: Throwable) {
                Log.e("KraViewModel", "Connection failed", e)
                ActionState.Error(server, e)
            }
        }
    }
}
