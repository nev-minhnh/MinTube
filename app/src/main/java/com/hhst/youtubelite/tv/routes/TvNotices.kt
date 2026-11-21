package com.hhst.youtubelite.tv.routes

import android.app.Activity
import com.hhst.youtubelite.core.AppNotice
import com.hhst.youtubelite.ui.noticeText
import com.hhst.youtubelite.ui.showMessage

/**
 * The TV form of a ViewModel notice: sign-in goes to the TV sign-in, text becomes a transient
 * message. No failure dialog (a touch surface with a copy button).
 */
fun Activity.renderTvNotice(notice: AppNotice) {
    when (notice) {
        AppNotice.SignIn -> startActivity(TvSignInRoute.intent(this))
        is AppNotice.LocalizedMessage -> showMessage(getString(notice.resource, *notice.arguments.toTypedArray()))
        is AppNotice.Message -> showMessage(noticeText(notice.text))
        is AppNotice.Failure -> showMessage(noticeText(notice.text, failure = true))
    }
}
