package flutter.plugins.contactsservice.contacts_service

import android.content.res.Resources
import android.database.Cursor
import android.provider.ContactsContract.CommonDataKinds
import java.util.HashMap

/**
 * Represents an object which has a label and a value
 * such as an email or a phone
 */
class Item(
    var label: String?,
    var value: String?,
    var type: Int
) {

    fun toMap(): HashMap<String, String?> {
        val result = HashMap<String, String?>()
        result["label"] = label
        result["value"] = value
        result["type"] = type.toString()
        return result
    }

    companion object {
        fun fromMap(map: HashMap<*, *>): Item {
            val label = map["label"] as String?
            val value = map["value"] as String?
            val type = map["type"] as String?
            return Item(label, value, type?.toIntOrNull() ?: -1)
        }

        fun getPhoneLabel(resources: Resources, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels) {
                val localizedLabel = CommonDataKinds.Phone.getTypeLabel(resources, type, "")
                localizedLabel.toString().lowercase()
            } else {
                when (type) {
                    CommonDataKinds.Phone.TYPE_HOME -> "home"
                    CommonDataKinds.Phone.TYPE_WORK -> "work"
                    CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                    CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax work"
                    CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax home"
                    CommonDataKinds.Phone.TYPE_MAIN -> "main"
                    CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "company"
                    CommonDataKinds.Phone.TYPE_PAGER -> "pager"
                    CommonDataKinds.Phone.TYPE_CUSTOM -> {
                        val labelColumnIndex = cursor.getColumnIndex(CommonDataKinds.Phone.LABEL)
                        if (labelColumnIndex != -1 && !cursor.isNull(labelColumnIndex)) {
                            cursor.getString(labelColumnIndex).lowercase()
                        } else ""
                    }
                    else -> "other"
                }
            }
        }

        fun getEmailLabel(resources: Resources, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels) {
                val localizedLabel = CommonDataKinds.Email.getTypeLabel(resources, type, "")
                localizedLabel.toString().lowercase()
            } else {
                when (type) {
                    CommonDataKinds.Email.TYPE_HOME -> "home"
                    CommonDataKinds.Email.TYPE_WORK -> "work"
                    CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
                    CommonDataKinds.Email.TYPE_CUSTOM -> {
                        val labelColumnIndex = cursor.getColumnIndex(CommonDataKinds.Email.LABEL)
                        if (labelColumnIndex != -1 && !cursor.isNull(labelColumnIndex)) {
                            cursor.getString(labelColumnIndex).lowercase()
                        } else ""
                    }
                    else -> "other"
                }
            }
        }
    }
}