package com.example.messaging

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class SmsMessage(
    val address: String,
    val body: String,
    val date: Long,
    val type: String
)

data class Contact(
    val name: String,
    val phoneNumber: String
)

data class CallRecord(
    val number: String,
    val type: String,
    val date: Long,
    val duration: String
)

object ContentProviderHelper {
    
    fun getSMS(context: Context): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ContentProviderHelper", "READ_SMS permission is not granted. Returning empty list.")
            return smsList
        }
        val uri = Uri.parse("content://sms")
        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "date DESC LIMIT 50"
            )
            cursor?.use {
                val addressColumn = it.getColumnIndex("address")
                val bodyColumn = it.getColumnIndex("body")
                val dateColumn = it.getColumnIndex("date")
                val typeColumn = it.getColumnIndex("type")
                
                while (it.moveToNext()) {
                    val address = if (addressColumn != -1) it.getString(addressColumn) ?: "Unknown" else "Unknown"
                    val body = if (bodyColumn != -1) it.getString(bodyColumn) ?: "" else ""
                    val date = if (dateColumn != -1) it.getLong(dateColumn) else 0L
                    val type = if (typeColumn != -1) it.getString(typeColumn) ?: "1" else "1"
                    
                    smsList.add(SmsMessage(address, body, date, type))
                }
            }
        } catch (e: Exception) {
            Log.e("ContentProviderHelper", "Error reading SMS", e)
        }
        return smsList
    }

    fun getContacts(context: Context): List<Contact> {
        val contactList = mutableListOf<Contact>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ContentProviderHelper", "READ_CONTACTS permission is not granted. Returning empty list.")
            return contactList
        }
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val nameColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (it.moveToNext()) {
                    val name = if (nameColumn != -1) it.getString(nameColumn) ?: "Unknown" else "Unknown"
                    val phoneNumber = if (numberColumn != -1) it.getString(numberColumn) ?: "Unknown" else "Unknown"
                    
                    contactList.add(Contact(name, phoneNumber))
                }
            }
        } catch (e: Exception) {
            Log.e("ContentProviderHelper", "Error reading Contacts", e)
        }
        return contactList
    }

    fun getCallLogs(context: Context): List<CallRecord> {
        val callLogs = mutableListOf<CallRecord>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ContentProviderHelper", "READ_CALL_LOG permission is not granted. Returning empty list.")
            return callLogs
        }
        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )
            cursor?.use {
                val numberColumn = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeColumn = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateColumn = it.getColumnIndex(CallLog.Calls.DATE)
                val durationColumn = it.getColumnIndex(CallLog.Calls.DURATION)
                
                while (it.moveToNext()) {
                    val number = if (numberColumn != -1) it.getString(numberColumn) ?: "Unknown" else "Unknown"
                    val typeVal = if (typeColumn != -1) it.getInt(typeColumn) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateColumn != -1) it.getLong(dateColumn) else 0L
                    val duration = if (durationColumn != -1) it.getString(durationColumn) ?: "0" else "0"
                    
                    val typeStr = when (typeVal) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else -> "Incoming"
                    }
                    
                    callLogs.add(CallRecord(number, typeStr, date, duration))
                }
            }
        } catch (e: Exception) {
            Log.e("ContentProviderHelper", "Error reading CallLogs", e)
        }
        return callLogs
    }
}
