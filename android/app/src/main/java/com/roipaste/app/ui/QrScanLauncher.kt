package com.roipaste.app.ui

import android.content.Context
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.roipaste.app.R

/**
 * Uses the Play services code scanner so we do not request CAMERA permission.
 * OnePlus Ace 5 builds without Play services fall back to pasting the QR JSON.
 */
object QrScanLauncher {
    fun start(context: Context, onRawValue: (String) -> Unit) {
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (availability != ConnectionResult.SUCCESS) {
            Toast.makeText(context, R.string.scan_qr_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (!value.isNullOrBlank()) {
                    onRawValue(value)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, R.string.scan_qr_unavailable, Toast.LENGTH_LONG).show()
            }
    }
}
