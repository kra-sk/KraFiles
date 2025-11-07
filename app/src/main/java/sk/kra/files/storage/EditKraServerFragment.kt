package sk.kra.files.storage

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import sk.kra.files.R
import sk.kra.files.databinding.EditKraServerFragmentBinding
import sk.kra.files.provider.kra.client.KraAuthority
import sk.kra.files.provider.kra.client.PasswordAuthentication
import sk.kra.files.util.ActionState
import sk.kra.files.util.ParcelableArgs
import sk.kra.files.util.args
import sk.kra.files.util.fadeToVisibilityUnsafe
import sk.kra.files.util.finish
import sk.kra.files.util.hideTextInputLayoutErrorOnTextChange
import sk.kra.files.util.isReady
import sk.kra.files.util.showToast
import sk.kra.files.util.takeIfNotEmpty
import sk.kra.files.util.viewModels

class EditKraServerFragment : Fragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { EditKraServerViewModel() } }

    private lateinit var binding: EditKraServerFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            launch { viewModel.connectState.collect { onConnectStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        EditKraServerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            activity.setTitle(
                if (args.server != null) {
                    R.string.storage_edit_kra_server_title_edit
                } else {
                    R.string.storage_edit_kra_server_title_add
                }
            )
        }

        binding.hostEdit.hideTextInputLayoutErrorOnTextChange(binding.hostLayout)
        binding.hostEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.portEdit.hideTextInputLayoutErrorOnTextChange(binding.portLayout)
        binding.usernameEdit.hideTextInputLayoutErrorOnTextChange(binding.usernameLayout)
        binding.usernameEdit.doAfterTextChanged { updateNamePlaceholder() }
        binding.passwordEdit.hideTextInputLayoutErrorOnTextChange(binding.passwordLayout)

        binding.saveOrConnectAndAddButton.setText(
            if (args.server != null) {
                R.string.save
            } else {
                R.string.storage_edit_kra_server_connect_and_add
            }
        )
        binding.saveOrConnectAndAddButton.setOnClickListener {
            if (args.server != null) {
                saveOrAdd()
            } else {
                connectAndAdd()
            }
        }
        binding.cancelButton.setOnClickListener { finish() }
        binding.removeOrAddButton.setText(
            if (args.server != null) R.string.remove else R.string.storage_edit_kra_server_add
        )
        binding.removeOrAddButton.setOnClickListener {
            if (args.server != null) {
                remove()
            } else {
                saveOrAdd()
            }
        }

        if (savedInstanceState == null) {
            val server = args.server
            if (server != null) {
                val authority = server.authority
                binding.hostEdit.setText(authority.host)
                if (authority.port != KraAuthority.DEFAULT_PORT) {
                    binding.portEdit.setText(authority.port.toString())
                }
                binding.usernameEdit.setText(authority.username)
                binding.passwordEdit.setText(server.authentication.password)
                binding.nameEdit.setText(server.customName)
            } else {
                // Set default values for new server
                binding.hostEdit.setText(KraAuthority.DEFAULT_HOST)
                binding.portEdit.setText(KraAuthority.DEFAULT_PORT.toString())
            }
        }
    }

    private fun updateNamePlaceholder() {
        val host = binding.hostEdit.text.toString().takeIfNotEmpty()
        val port = binding.portEdit.text.toString().takeIfNotEmpty()?.toIntOrNull()
            ?: KraAuthority.DEFAULT_PORT
        val username = binding.usernameEdit.text.toString().takeIfNotEmpty()

        val authority = if (host != null && username != null) {
            try {
                KraAuthority(host, port, username)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        binding.nameLayout.placeholderText = authority?.toString()
    }

    private fun saveOrAdd() {
        val host = binding.hostEdit.text.toString().takeIfNotEmpty()
        if (host.isNullOrEmpty()) {
            binding.hostLayout.error = getString(R.string.storage_edit_kra_server_host_error_empty)
            return
        }

        val port = binding.portEdit.text.toString().takeIfNotEmpty()?.toIntOrNull()
            ?: KraAuthority.DEFAULT_PORT
        if (port !in 1..65535) {
            binding.portLayout.error = getString(R.string.storage_edit_kra_server_port_error_invalid)
            return
        }

        val username = binding.usernameEdit.text.toString().takeIfNotEmpty()
        if (username.isNullOrEmpty()) {
            binding.usernameLayout.error = getString(R.string.storage_edit_kra_server_username_error_empty)
            return
        }

        val password = binding.passwordEdit.text.toString()
        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.storage_edit_kra_server_password_error_empty)
            return
        }

        val customName = binding.nameEdit.text.toString().takeIfNotEmpty()

        val authority = KraAuthority(host, port, username)
        val authentication = PasswordAuthentication(password)
        addOrReplace(authority, authentication, customName)
    }

    private fun connectAndAdd() {
        if (!viewModel.connectState.value.isReady) {
            return
        }

        val host = binding.hostEdit.text.toString().takeIfNotEmpty()
        if (host.isNullOrEmpty()) {
            binding.hostLayout.error = getString(R.string.storage_edit_kra_server_host_error_empty)
            return
        }

        val port = binding.portEdit.text.toString().takeIfNotEmpty()?.toIntOrNull()
            ?: KraAuthority.DEFAULT_PORT
        if (port !in 1..65535) {
            binding.portLayout.error = getString(R.string.storage_edit_kra_server_port_error_invalid)
            return
        }

        val username = binding.usernameEdit.text.toString().takeIfNotEmpty()
        if (username.isNullOrEmpty()) {
            binding.usernameLayout.error = getString(R.string.storage_edit_kra_server_username_error_empty)
            return
        }

        val password = binding.passwordEdit.text.toString()
        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.storage_edit_kra_server_password_error_empty)
            return
        }

        val customName = binding.nameEdit.text.toString().takeIfNotEmpty()

        val authority = KraAuthority(host, port, username)
        val authentication = PasswordAuthentication(password)
        viewModel.connect(authority, authentication, customName)
    }

    private fun remove() {
        Storages.remove(args.server!!)
        finish()
    }

    private fun addOrReplace(
        authority: KraAuthority,
        authentication: PasswordAuthentication,
        customName: String?
    ) {
        val server = KraServer(
            id = args.server?.id,
            customName = customName,
            authority = authority,
            authentication = authentication
        )
        Storages.addOrReplace(server)
        finish()
    }

    private fun onConnectStateChanged(state: ActionState<KraServer, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> {
                val isReady = state.isReady
                binding.progress.fadeToVisibilityUnsafe(!isReady)
                binding.scrollView.fadeToVisibilityUnsafe(isReady)
                binding.saveOrConnectAndAddButton.isEnabled = isReady
                binding.cancelButton.isEnabled = isReady
                binding.removeOrAddButton.isEnabled = isReady
            }
            is ActionState.Success -> {
                Storages.addOrReplace(state.argument)
                finish()
            }
            is ActionState.Error -> {
                val throwable = state.throwable
                throwable.printStackTrace()
                showToast(throwable.toString())
            }
        }
    }

    @Parcelize
    class Args(val server: KraServer?) : ParcelableArgs
}
