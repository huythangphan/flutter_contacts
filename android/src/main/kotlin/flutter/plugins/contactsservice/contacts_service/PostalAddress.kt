package flutter.plugins.contactsservice.contacts_service

import android.annotation.TargetApi
import android.content.res.Resources
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.util.HashMap

@TargetApi(Build.VERSION_CODES.ECLAIR)
class PostalAddress(
    var label: String?,
    var street: String?,
    var city: String?,
    var postcode: String?,
    var region: String?,
    var country: String?,
    var type: Int
) {

    fun toMap(): HashMap<String, String?> {
        val result = HashMap<String, String?>()
        result["label"] = label
        result["street"] = street
        result["city"] = city
        result["postcode"] = postcode
        result["region"] = region
        result["country"] = country
        result["type"] = type.toString()
        return result
    }

    companion object {
        fun fromMap(map: HashMap<*, *>): PostalAddress {
            val label = map["label"] as String?
            val street = map["street"] as String?
            val city = map["city"] as String?
            val postcode = map["postcode"] as String?
            val region = map["region"] as String?
            val country = map["country"] as String?
            val type = map["type"] as String?
            return PostalAddress(label, street, city, postcode, region, country, type?.toIntOrNull() ?: -1)
        }

        fun getLabel(resources: Resources?, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels && resources != null) {
                val localizedLabel = CommonDataKinds.StructuredPostal.getTypeLabel(resources, type, "")
                localizedLabel.toString().lowercase()
            } else {
                val columnIndex = cursor.getColumnIndex(StructuredPostal.TYPE)
                when (cursor.getInt(columnIndex)) {
                    StructuredPostal.TYPE_HOME -> "home"
                    StructuredPostal.TYPE_WORK -> "work"
                    StructuredPostal.TYPE_CUSTOM -> {
                        val labelColumnIndex = cursor.getColumnIndex(StructuredPostal.LABEL)
                        if (labelColumnIndex != -1 && !cursor.isNull(labelColumnIndex)) {
                            cursor.getString(labelColumnIndex)
                        } else ""
                    }
                    else -> "other"
                }
            }
        }
    }
}