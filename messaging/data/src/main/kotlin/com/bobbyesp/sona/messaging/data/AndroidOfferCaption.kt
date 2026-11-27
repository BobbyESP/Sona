/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import android.content.Context
import com.bobbyesp.sona.messaging.domain.OfferCaption
import com.bobbyesp.sona.messaging.domain.readableSize

/** The words above a file offered, in the user's language. */
fun Context.offerCaption(): OfferCaption = OfferCaption { name, size ->
    getString(R.string.messages_offer_caption, name, readableSize(size))
}
