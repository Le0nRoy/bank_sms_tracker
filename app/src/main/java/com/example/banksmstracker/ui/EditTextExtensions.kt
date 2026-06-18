package com.example.banksmstracker.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.example.banksmstracker.R

internal fun EditText.setSimpleWatcher(onChange: (String) -> Unit) {
    val existingWatcher = getTag(R.id.text_watcher_tag) as? TextWatcher
    if (existingWatcher != null) {
        removeTextChangedListener(existingWatcher)
    }

    val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onChange(s?.toString().orEmpty())
        }
    }
    addTextChangedListener(watcher)
    setTag(R.id.text_watcher_tag, watcher)
}
