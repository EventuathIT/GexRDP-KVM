package com.eventuath.gexrdp_kvm.core.input

import android.view.KeyEvent

/**
 * Android KeyEvent -> Linux input-event-codes (evdev). The server maps these
 * to QEMU QKeyCodes for `input-send-event`.
 */
object KeyMap {

    data class CharKey(val evdev: Int, val shift: Boolean)

    fun evdev(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_A -> 30
        KeyEvent.KEYCODE_B -> 48
        KeyEvent.KEYCODE_C -> 46
        KeyEvent.KEYCODE_D -> 32
        KeyEvent.KEYCODE_E -> 18
        KeyEvent.KEYCODE_F -> 33
        KeyEvent.KEYCODE_G -> 34
        KeyEvent.KEYCODE_H -> 35
        KeyEvent.KEYCODE_I -> 23
        KeyEvent.KEYCODE_J -> 36
        KeyEvent.KEYCODE_K -> 37
        KeyEvent.KEYCODE_L -> 38
        KeyEvent.KEYCODE_M -> 50
        KeyEvent.KEYCODE_N -> 49
        KeyEvent.KEYCODE_O -> 24
        KeyEvent.KEYCODE_P -> 25
        KeyEvent.KEYCODE_Q -> 16
        KeyEvent.KEYCODE_R -> 19
        KeyEvent.KEYCODE_S -> 31
        KeyEvent.KEYCODE_T -> 20
        KeyEvent.KEYCODE_U -> 22
        KeyEvent.KEYCODE_V -> 47
        KeyEvent.KEYCODE_W -> 17
        KeyEvent.KEYCODE_X -> 45
        KeyEvent.KEYCODE_Y -> 21
        KeyEvent.KEYCODE_Z -> 44

        KeyEvent.KEYCODE_0 -> 11
        KeyEvent.KEYCODE_1 -> 2
        KeyEvent.KEYCODE_2 -> 3
        KeyEvent.KEYCODE_3 -> 4
        KeyEvent.KEYCODE_4 -> 5
        KeyEvent.KEYCODE_5 -> 6
        KeyEvent.KEYCODE_6 -> 7
        KeyEvent.KEYCODE_7 -> 8
        KeyEvent.KEYCODE_8 -> 9
        KeyEvent.KEYCODE_9 -> 10

        KeyEvent.KEYCODE_SPACE -> 57
        KeyEvent.KEYCODE_ENTER -> 28
        KeyEvent.KEYCODE_NUMPAD_ENTER -> 96
        KeyEvent.KEYCODE_ESCAPE -> 1
        KeyEvent.KEYCODE_DEL -> 14
        KeyEvent.KEYCODE_FORWARD_DEL -> 111
        KeyEvent.KEYCODE_TAB -> 15
        KeyEvent.KEYCODE_CAPS_LOCK -> 58
        KeyEvent.KEYCODE_NUM_LOCK -> 69
        KeyEvent.KEYCODE_SCROLL_LOCK -> 70
        KeyEvent.KEYCODE_INSERT -> 110

        KeyEvent.KEYCODE_SHIFT_LEFT -> 42
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 54
        KeyEvent.KEYCODE_CTRL_LEFT -> 29
        KeyEvent.KEYCODE_CTRL_RIGHT -> 97
        KeyEvent.KEYCODE_ALT_LEFT -> 56
        KeyEvent.KEYCODE_ALT_RIGHT -> 100
        KeyEvent.KEYCODE_META_LEFT -> 125
        KeyEvent.KEYCODE_META_RIGHT -> 126

        KeyEvent.KEYCODE_DPAD_UP -> 103
        KeyEvent.KEYCODE_DPAD_DOWN -> 108
        KeyEvent.KEYCODE_DPAD_LEFT -> 105
        KeyEvent.KEYCODE_DPAD_RIGHT -> 106
        KeyEvent.KEYCODE_DPAD_CENTER -> 28
        KeyEvent.KEYCODE_MOVE_HOME -> 102
        KeyEvent.KEYCODE_MOVE_END -> 107
        KeyEvent.KEYCODE_PAGE_UP -> 104
        KeyEvent.KEYCODE_PAGE_DOWN -> 109

        KeyEvent.KEYCODE_F1 -> 59
        KeyEvent.KEYCODE_F2 -> 60
        KeyEvent.KEYCODE_F3 -> 61
        KeyEvent.KEYCODE_F4 -> 62
        KeyEvent.KEYCODE_F5 -> 63
        KeyEvent.KEYCODE_F6 -> 64
        KeyEvent.KEYCODE_F7 -> 65
        KeyEvent.KEYCODE_F8 -> 66
        KeyEvent.KEYCODE_F9 -> 67
        KeyEvent.KEYCODE_F10 -> 68
        KeyEvent.KEYCODE_F11 -> 87
        KeyEvent.KEYCODE_F12 -> 88

        KeyEvent.KEYCODE_MINUS -> 12
        KeyEvent.KEYCODE_EQUALS -> 13
        KeyEvent.KEYCODE_LEFT_BRACKET -> 26
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 27
        KeyEvent.KEYCODE_BACKSLASH -> 43
        KeyEvent.KEYCODE_SEMICOLON -> 39
        KeyEvent.KEYCODE_APOSTROPHE -> 40
        KeyEvent.KEYCODE_GRAVE -> 41
        KeyEvent.KEYCODE_COMMA -> 51
        KeyEvent.KEYCODE_PERIOD -> 52
        KeyEvent.KEYCODE_SLASH -> 53
        KeyEvent.KEYCODE_STAR -> 55
        KeyEvent.KEYCODE_PLUS -> 78

        KeyEvent.KEYCODE_NUMPAD_0 -> 82
        KeyEvent.KEYCODE_NUMPAD_1 -> 79
        KeyEvent.KEYCODE_NUMPAD_2 -> 80
        KeyEvent.KEYCODE_NUMPAD_3 -> 81
        KeyEvent.KEYCODE_NUMPAD_4 -> 75
        KeyEvent.KEYCODE_NUMPAD_5 -> 76
        KeyEvent.KEYCODE_NUMPAD_6 -> 77
        KeyEvent.KEYCODE_NUMPAD_7 -> 71
        KeyEvent.KEYCODE_NUMPAD_8 -> 72
        KeyEvent.KEYCODE_NUMPAD_9 -> 73
        KeyEvent.KEYCODE_NUMPAD_DOT -> 83
        KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 98
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 55
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 74
        KeyEvent.KEYCODE_NUMPAD_ADD -> 78

        else -> null
    }

    fun modsOf(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 1
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 1 shl 1
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 1 shl 2
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> 1 shl 3
        else -> 0
    }

    private val unshifted = mapOf(
        'a' to 30, 'b' to 48, 'c' to 46, 'd' to 32, 'e' to 18, 'f' to 33, 'g' to 34,
        'h' to 35, 'i' to 23, 'j' to 36, 'k' to 37, 'l' to 38, 'm' to 50, 'n' to 49,
        'o' to 24, 'p' to 25, 'q' to 16, 'r' to 19, 's' to 31, 't' to 20, 'u' to 22,
        'v' to 47, 'w' to 17, 'x' to 45, 'y' to 21, 'z' to 44,
        '1' to 2, '2' to 3, '3' to 4, '4' to 5, '5' to 6,
        '6' to 7, '7' to 8, '8' to 9, '9' to 10, '0' to 11,
        ' ' to 57, '\n' to 28, '\t' to 15,
        '-' to 12, '=' to 13, '[' to 26, ']' to 27, '\\' to 43, ';' to 39,
        '\'' to 40, '`' to 41, ',' to 51, '.' to 52, '/' to 53,
    )

    private val shifted = mapOf(
        '!' to 2, '@' to 3, '#' to 4, '$' to 5, '%' to 6, '^' to 7, '&' to 8,
        '*' to 9, '(' to 10, ')' to 11, '_' to 12, '+' to 13, '{' to 26, '}' to 27,
        '|' to 43, ':' to 39, '"' to 40, '~' to 41, '<' to 51, '>' to 52, '?' to 53,
    )

    fun charToKey(char: Char): CharKey? {
        unshifted[char]?.let { return CharKey(it, false) }
        shifted[char]?.let { return CharKey(it, true) }
        if (char.isUpperCase()) {
            unshifted[char.lowercaseChar()]?.let { return CharKey(it, true) }
        }
        return null
    }
}
