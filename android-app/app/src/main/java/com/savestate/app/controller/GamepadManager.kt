package com.savestate.app.controller

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamepadAction {
    BACKUP, RESTORE, MANAGE_BACKUPS, DELETE,
    NEW_PROFILE, SETTINGS, NAV_UP, NAV_DOWN, PAGE_UP, PAGE_DOWN, NONE
}

class GamepadManager(private val context: Context) {
    private var buttonMappings = mapOf<Int, GamepadAction>(
        KeyEvent.KEYCODE_BUTTON_A to GamepadAction.BACKUP,
        KeyEvent.KEYCODE_BUTTON_B to GamepadAction.DELETE,
        KeyEvent.KEYCODE_BUTTON_X to GamepadAction.RESTORE,
        KeyEvent.KEYCODE_BUTTON_Y to GamepadAction.MANAGE_BACKUPS,
        KeyEvent.KEYCODE_BUTTON_START to GamepadAction.NEW_PROFILE,
        KeyEvent.KEYCODE_BUTTON_SELECT to GamepadAction.SETTINGS,
        KeyEvent.KEYCODE_BUTTON_L1 to GamepadAction.PAGE_UP,
        KeyEvent.KEYCODE_BUTTON_R1 to GamepadAction.PAGE_DOWN
    )

    fun updateMappings(newMappings: Map<Int, GamepadAction>) {
        buttonMappings = newMappings
    }

    private val _controllerMode = MutableStateFlow(false)
    val controllerMode: StateFlow<Boolean> = _controllerMode.asStateFlow()

    private val _controllerConnected = MutableStateFlow(false)
    val controllerConnected: StateFlow<Boolean> = _controllerConnected.asStateFlow()

    private val _focusedProfileIndex = MutableStateFlow(-1)
    val focusedProfileIndex: StateFlow<Int> = _focusedProfileIndex.asStateFlow()

    private val _dialogOpen = MutableStateFlow(false)
    var dialogOpen: Boolean
        get() = _dialogOpen.value
        set(value) {
            _dialogOpen.value = value
            if (value) {
                stopRepeat()
            }
        }

    private var profileCount = 0
    private var actionCallback: ((GamepadAction) -> Unit)? = null
    private var dialogKeyCallback: ((KeyEvent) -> Boolean)? = null

    fun setDialogKeyCallback(callback: ((KeyEvent) -> Boolean)?) {
        dialogKeyCallback = callback
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var repeatJob: Job? = null
    private var activeRepeatKeyCode: Int? = null

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    init {
        updateControllerConnected()
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                updateControllerConnected()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                updateControllerConnected()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                updateControllerConnected()
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun setProfileCount(count: Int) {
        profileCount = count
        if (count == 0) {
            _focusedProfileIndex.value = -1
        } else if (_focusedProfileIndex.value >= count) {
            _focusedProfileIndex.value = count - 1
        } else if (_focusedProfileIndex.value == -1 && _controllerMode.value) {
            _focusedProfileIndex.value = 0
        }
    }

    fun setActionCallback(callback: (GamepadAction) -> Unit) {
        actionCallback = callback
    }

    fun onTouchEvent() {
        if (_controllerMode.value) {
            _controllerMode.value = false
            stopRepeat()
        }
    }

    fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        val isGamepad = isGamepadEvent(keyEvent)
        if (isGamepad && !_controllerMode.value) {
            _controllerMode.value = true
            if (profileCount > 0 && _focusedProfileIndex.value == -1) {
                _focusedProfileIndex.value = 0
            }
        }

        if (!isGamepad) {
            return false
        }

        if (dialogOpen) {
            if (dialogKeyCallback?.invoke(keyEvent) == true) {
                return true
            }
            // Let dialogs consume their own keys, but allow back/B to work for dismissal
            if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK || keyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                return false
            }
            return true
        }

        val keyCode = keyEvent.keyCode
        val action = keyEvent.action

        if (action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    startRepeat(keyCode, GamepadAction.NAV_UP)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    startRepeat(keyCode, GamepadAction.NAV_DOWN)
                    return true
                }
                else -> {
                    val mappedAction = buttonMappings[keyCode]
                    if (mappedAction != null && mappedAction != GamepadAction.NONE) {
                        triggerAction(mappedAction)
                        return true
                    }
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == activeRepeatKeyCode) {
                stopRepeat()
                return true
            }
            if (keyCode in buttonMappings.keys || keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true
            }
        }

        return false
    }

    private fun isGamepadEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode in listOf(
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER
            )) return true

        val source = event.source
        return (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun updateControllerConnected() {
        val deviceIds = InputDevice.getDeviceIds()
        var connected = false
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isGamepadDevice = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepadDevice && !device.isVirtual) {
                connected = true
                break
            }
        }
        _controllerConnected.value = connected
    }

    private fun startRepeat(keyCode: Int, action: GamepadAction) {
        if (activeRepeatKeyCode == keyCode) return
        stopRepeat()
        activeRepeatKeyCode = keyCode
        repeatJob = scope.launch {
            triggerAction(action)
            delay(450)
            while (isActive) {
                triggerAction(action)
                delay(120)
            }
        }
    }

    private fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        activeRepeatKeyCode = null
    }

    private fun triggerAction(action: GamepadAction) {
        when (action) {
            GamepadAction.NAV_UP -> {
                if (profileCount > 0) {
                    val current = _focusedProfileIndex.value
                    if (current <= 0) {
                        _focusedProfileIndex.value = profileCount - 1
                    } else {
                        _focusedProfileIndex.value = current - 1
                    }
                }
            }
            GamepadAction.NAV_DOWN -> {
                if (profileCount > 0) {
                    val current = _focusedProfileIndex.value
                    if (current == -1 || current >= profileCount - 1) {
                        _focusedProfileIndex.value = 0
                    } else {
                        _focusedProfileIndex.value = current + 1
                    }
                }
            }
            GamepadAction.PAGE_UP -> {
                if (profileCount > 0) {
                    val current = _focusedProfileIndex.value
                    if (current <= 0) {
                        _focusedProfileIndex.value = profileCount - 1
                    } else {
                        _focusedProfileIndex.value = (current - 5).coerceAtLeast(0)
                    }
                }
            }
            GamepadAction.PAGE_DOWN -> {
                if (profileCount > 0) {
                    val current = _focusedProfileIndex.value
                    if (current == -1 || current >= profileCount - 1) {
                        _focusedProfileIndex.value = 0
                    } else {
                        _focusedProfileIndex.value = (current + 5).coerceAtMost(profileCount - 1)
                    }
                }
            }
            else -> {
                actionCallback?.invoke(action)
            }
        }
    }
}
