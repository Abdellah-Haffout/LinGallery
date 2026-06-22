package com.soufianodev.lingallery.shared.desktop

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
}

fun copyImageToClipboard(image: java.awt.Image) {
    val transferable = object : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor?) =
            flavor == java.awt.datatransfer.DataFlavor.imageFlavor
        override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor?) = image
    }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
}
