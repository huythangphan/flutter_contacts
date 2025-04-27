package flutter.plugins.contactsservice.contacts_service

import java.util.ArrayList
import java.util.HashMap

class Contact : Comparable<Contact> {
    
    constructor(id: String) {
        this.identifier = id
    }
    
    private constructor()
    
    var identifier: String? = null
    var displayName: String? = null
    var givenName: String? = null
    var middleName: String? = null
    var familyName: String? = null
    var prefix: String? = null
    var suffix: String? = null
    var company: String? = null
    var jobTitle: String? = null
    var note: String? = null
    var birthday: String? = null
    var androidAccountType: String? = null
    var androidAccountName: String? = null
    var emails: ArrayList<Item> = ArrayList()
    var phones: ArrayList<Item> = ArrayList()
    var postalAddresses: ArrayList<PostalAddress> = ArrayList()
    var avatar: ByteArray = ByteArray(0)
    
    fun toMap(): HashMap<String, Any?> {
        val contactMap = HashMap<String, Any?>()
        contactMap["identifier"] = identifier
        contactMap["displayName"] = displayName
        contactMap["givenName"] = givenName
        contactMap["middleName"] = middleName
        contactMap["familyName"] = familyName
        contactMap["prefix"] = prefix
        contactMap["suffix"] = suffix
        contactMap["company"] = company
        contactMap["jobTitle"] = jobTitle
        contactMap["avatar"] = avatar
        contactMap["note"] = note
        contactMap["birthday"] = birthday
        contactMap["androidAccountType"] = androidAccountType
        contactMap["androidAccountName"] = androidAccountName
        
        val emailsMap = ArrayList<HashMap<String, String?>>()
        for (email in emails) {
            emailsMap.add(email.toMap())
        }
        contactMap["emails"] = emailsMap
        
        val phonesMap = ArrayList<HashMap<String, String?>>()
        for (phone in phones) {
            phonesMap.add(phone.toMap())
        }
        contactMap["phones"] = phonesMap
        
        val addressesMap = ArrayList<HashMap<String, String?>>()
        for (address in postalAddresses) {
            addressesMap.add(address.toMap())
        }
        contactMap["postalAddresses"] = addressesMap
        
        return contactMap
    }
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: HashMap<*, *>): Contact {
            val contact = Contact()
            contact.identifier = map["identifier"] as String?
            contact.givenName = map["givenName"] as String?
            contact.middleName = map["middleName"] as String?
            contact.familyName = map["familyName"] as String?
            contact.prefix = map["prefix"] as String?
            contact.suffix = map["suffix"] as String?
            contact.company = map["company"] as String?
            contact.jobTitle = map["jobTitle"] as String?
            contact.avatar = map["avatar"] as? ByteArray ?: ByteArray(0)
            contact.note = map["note"] as String?
            contact.birthday = map["birthday"] as String?
            contact.androidAccountType = map["androidAccountType"] as String?
            contact.androidAccountName = map["androidAccountName"] as String?
            
            val emails = map["emails"] as? ArrayList<HashMap<*, *>>
            emails?.forEach { email ->
                contact.emails.add(Item.fromMap(email))
            }
            
            val phones = map["phones"] as? ArrayList<HashMap<*, *>>
            phones?.forEach { phone ->
                contact.phones.add(Item.fromMap(phone))
            }
            
            val postalAddresses = map["postalAddresses"] as? ArrayList<HashMap<*, *>>
            postalAddresses?.forEach { postalAddress ->
                contact.postalAddresses.add(PostalAddress.fromMap(postalAddress))
            }
            
            return contact
        }
    }
    
    override fun compareTo(other: Contact): Int {
        val givenName1 = this.givenName?.lowercase() ?: ""
        val givenName2 = other.givenName?.lowercase() ?: ""
        return givenName1.compareTo(givenName2)
    }
}