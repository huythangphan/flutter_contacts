package flutter.plugins.contactsservice.contacts_service

import android.annotation.TargetApi
import android.app.Activity.RESULT_CANCELED
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.text.TextUtils
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.ECLAIR)
class ContactsServicePlugin : MethodCallHandler, FlutterPlugin, ActivityAware {

    companion object {
        private const val FORM_OPERATION_CANCELED = 1
        private const val FORM_COULD_NOT_BE_OPEN = 2

        private const val LOG_TAG = "flutter_contacts"

        private const val REQUEST_OPEN_CONTACT_FORM = 52941
        private const val REQUEST_OPEN_EXISTING_CONTACT = 52942
        private const val REQUEST_OPEN_CONTACT_PICKER = 52943

        private val PROJECTION = arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Profile.DISPLAY_NAME,
                ContactsContract.Contacts.Data.MIMETYPE,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                StructuredName.DISPLAY_NAME,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.PREFIX,
                StructuredName.SUFFIX,
                CommonDataKinds.Note.NOTE,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                Email.DATA,
                Email.ADDRESS,
                Email.TYPE,
                Email.LABEL,
                Organization.COMPANY,
                Organization.TITLE,
                StructuredPostal.FORMATTED_ADDRESS,
                StructuredPostal.TYPE,
                StructuredPostal.LABEL,
                StructuredPostal.STREET,
                StructuredPostal.POBOX,
                StructuredPostal.NEIGHBORHOOD,
                StructuredPostal.CITY,
                StructuredPostal.REGION,
                StructuredPostal.POSTCODE,
                StructuredPostal.COUNTRY
        )

        private fun loadContactPhotoHighRes(identifier: String,
                                           photoHighResolution: Boolean, contentResolver: ContentResolver): ByteArray? {
            try {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, identifier.toLong())
                val input = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, photoHighResolution)
                        ?: return null

                val bitmap = BitmapFactory.decodeStream(input)
                input.close()

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                stream.close()
                return bytes
            } catch (ex: IOException) {
                Log.e(LOG_TAG, ex.message ?: "Error loading contact photo")
                return null
            }
        }
    }

    private var contentResolver: ContentResolver? = null
    private var methodChannel: MethodChannel? = null
    private var delegate: BaseContactsServiceDelegate? = null
    private var resources: Resources? = null

    private val executor: ExecutorService = ThreadPoolExecutor(0, 10, 60, TimeUnit.SECONDS, ArrayBlockingQueue(1000))

    private fun initDelegateWith(context: Context) {
        this.delegate = ContactServiceDelegate(context)
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        initInstance(binding.binaryMessenger, binding.applicationContext)
        initDelegateWith(binding.applicationContext)
    }

    private fun initInstance(messenger: BinaryMessenger, context: Context) {
        methodChannel = MethodChannel(messenger, "github.com/clovisnicolas/flutter_contacts")
        methodChannel?.setMethodCallHandler(this)
        this.contentResolver = context.contentResolver
        // Initialize resources from the context
        this.resources = context.resources
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        contentResolver = null
        delegate = null
        resources = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getContacts" -> {
                getContacts(call.method, call.argument<String>("query"), 
                    call.argument<Boolean>("withThumbnails") ?: false,
                    call.argument<Boolean>("photoHighResolution") ?: false,
                    call.argument<Boolean>("orderByGivenName") ?: false,
                    call.argument<Boolean>("androidLocalizedLabels") ?: false, result)
            }
            "getContactsForPhone" -> {
                getContactsForPhone(call.method, call.argument<String>("phone") ?: "",
                    call.argument<Boolean>("withThumbnails") ?: false,
                    call.argument<Boolean>("photoHighResolution") ?: false,
                    call.argument<Boolean>("orderByGivenName") ?: false,
                    call.argument<Boolean>("androidLocalizedLabels") ?: false, result)
            }
            "getContactsForEmail" -> {
                getContactsForEmail(call.method, call.argument<String>("email") ?: "",
                    call.argument<Boolean>("withThumbnails") ?: false,
                    call.argument<Boolean>("photoHighResolution") ?: false,
                    call.argument<Boolean>("orderByGivenName") ?: false,
                    call.argument<Boolean>("androidLocalizedLabels") ?: false, result)
            }
            "getAvatar" -> {
                val contact = Contact.fromMap(call.argument<HashMap<*, *>>("contact") as HashMap<*, *>)
                getAvatar(contact, call.argument<Boolean>("photoHighResolution") ?: false, result)
            }
            "addContact" -> {
                val contact = Contact.fromMap(call.arguments as HashMap<*, *>)
                if (addContact(contact)) {
                    result.success(null)
                } else {
                    result.error(null.toString(), "Failed to add the contact", null)
                }
            }
            "deleteContact" -> {
                val contact = Contact.fromMap(call.arguments as HashMap<*, *>)
                if (deleteContact(contact)) {
                    result.success(null)
                } else {
                    result.error(null.toString(), "Failed to delete the contact, make sure it has a valid identifier", null)
                }
            }
            "updateContact" -> {
                val contact = Contact.fromMap(call.arguments as HashMap<*, *>)
                if (updateContact(contact)) {
                    result.success(null)
                } else {
                    result.error(null.toString(), "Failed to update the contact, make sure it has a valid identifier", null)
                }
            }
            "openExistingContact" -> {
                val contact = Contact.fromMap(call.argument<HashMap<*, *>>("contact") as HashMap<*, *>)
                val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false
                delegate?.let {
                    it.setResult(result)
                    it.setLocalizedLabels(localizedLabels)
                    it.openExistingContact(contact)
                } ?: result.success(FORM_COULD_NOT_BE_OPEN)
            }
            "openContactForm" -> {
                val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false
                delegate?.let {
                    it.setResult(result)
                    it.setLocalizedLabels(localizedLabels)
                    it.openContactForm()
                } ?: result.success(FORM_COULD_NOT_BE_OPEN)
            }
            "openDeviceContactPicker" -> {
                val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false
                openDeviceContactPicker(result, localizedLabels)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private fun getContacts(callMethod: String, query: String?, withThumbnails: Boolean, 
                           photoHighResolution: Boolean, orderByGivenName: Boolean, 
                           localizedLabels: Boolean, result: Result) {
        GetContactsTask(callMethod, result, withThumbnails, photoHighResolution, 
                       orderByGivenName, localizedLabels).executeOnExecutor(executor, query, false)
    }

    private fun getContactsForPhone(callMethod: String, phone: String, withThumbnails: Boolean, 
                                   photoHighResolution: Boolean, orderByGivenName: Boolean, 
                                   localizedLabels: Boolean, result: Result) {
        GetContactsTask(callMethod, result, withThumbnails, photoHighResolution, 
                       orderByGivenName, localizedLabels).executeOnExecutor(executor, phone, true)
    }

    private fun getContactsForEmail(callMethod: String, email: String, withThumbnails: Boolean, 
                                   photoHighResolution: Boolean, orderByGivenName: Boolean, 
                                   localizedLabels: Boolean, result: Result) {
        GetContactsTask(callMethod, result, withThumbnails, photoHighResolution, 
                       orderByGivenName, localizedLabels).executeOnExecutor(executor, email, true)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        (delegate as? ContactServiceDelegate)?.bindToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        (delegate as? ContactServiceDelegate)?.unbindActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        (delegate as? ContactServiceDelegate)?.bindToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        (delegate as? ContactServiceDelegate)?.unbindActivity()
    }

    open inner class BaseContactsServiceDelegate : PluginRegistry.ActivityResultListener {
        private var result: Result? = null
        private var localizedLabels: Boolean = false

        fun setResult(result: Result) {
            this.result = result
        }

        fun setLocalizedLabels(localizedLabels: Boolean) {
            this.localizedLabels = localizedLabels
        }

        fun finishWithResult(result: Any) {
            this.result?.success(result)
            this.result = null
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
            if (requestCode == REQUEST_OPEN_EXISTING_CONTACT || requestCode == REQUEST_OPEN_CONTACT_FORM) {
                try {
                    val uri = intent?.data
                    getContactByIdentifier(uri?.lastPathSegment)?.let { finishWithResult(it) }
                } catch (e: NullPointerException) {
                    finishWithResult(FORM_OPERATION_CANCELED)
                }
                return true
            }

            if (requestCode == REQUEST_OPEN_CONTACT_PICKER) {
                if (resultCode == RESULT_CANCELED) {
                    finishWithResult(FORM_OPERATION_CANCELED)
                    return true
                }
                val contactUri = intent?.data
                if (intent != null && contactUri != null) {
                    contentResolver?.query(contactUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = contactUri.lastPathSegment
                            getContacts("openDeviceContactPicker", id, false, false, false, localizedLabels, this.result!!)
                        } else {
                            Log.e(LOG_TAG, "onActivityResult - cursor.moveToFirst() returns false")
                            finishWithResult(FORM_OPERATION_CANCELED)
                        }
                    }
                } else {
                    return true
                }
                return true
            }

            finishWithResult(FORM_COULD_NOT_BE_OPEN)
            return false
        }

        fun openExistingContact(contact: Contact) {
            val identifier = contact.identifier
            try {
                val contactMapFromDevice = getContactByIdentifier(identifier)
                // Contact existence check
                if (contactMapFromDevice != null) {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, identifier)
                    val intent = Intent(Intent.ACTION_EDIT)
                    intent.setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    intent.putExtra("finishActivityOnSaveCompleted", true)
                    startIntent(intent, REQUEST_OPEN_EXISTING_CONTACT)
                } else {
                    finishWithResult(FORM_COULD_NOT_BE_OPEN)
                }
            } catch (e: Exception) {
                finishWithResult(FORM_COULD_NOT_BE_OPEN)
            }
        }

        fun openContactForm() {
            try {
                val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                intent.putExtra("finishActivityOnSaveCompleted", true)
                startIntent(intent, REQUEST_OPEN_CONTACT_FORM)
            } catch (e: Exception) {
                // Handle exception
            }
        }

        fun openContactPicker() {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = ContactsContract.Contacts.CONTENT_TYPE
            startIntent(intent, REQUEST_OPEN_CONTACT_PICKER)
        }

                open fun startIntent(intent: Intent, request: Int) {
            // Base implementation is empty, will be overridden in subclasses
        }

        private fun getContactByIdentifier(identifier: String?): HashMap<*, *>? {
            if (identifier == null) return null

            var matchingContacts: ArrayList<Contact>? = null;
            contentResolver?.query(
                ContactsContract.Data.CONTENT_URI, PROJECTION,
                ContactsContract.RawContacts.CONTACT_ID + " = ?",
                arrayOf(identifier),
                null
            )?.use { cursor ->
                matchingContacts = getContactsFrom(cursor, localizedLabels)
            } ?: return null

            return if (!matchingContacts.isNullOrEmpty()) {
                matchingContacts!!.first().toMap()
            } else {
                null
            }
        }
    }
    
    private fun openDeviceContactPicker(result: Result, localizedLabels: Boolean) {
        delegate?.let {
            it.setResult(result)
            it.setLocalizedLabels(localizedLabels)
            it.openContactPicker()
        } ?: result.success(FORM_COULD_NOT_BE_OPEN)
    }
    
    inner class ContactServiceDelegate(private val context: Context) : BaseContactsServiceDelegate() {

        private var activityPluginBinding: ActivityPluginBinding? = null

        fun bindToActivity(activityPluginBinding: ActivityPluginBinding) {
            this.activityPluginBinding = activityPluginBinding
            this.activityPluginBinding?.addActivityResultListener(this)
        }

        fun unbindActivity() {
            this.activityPluginBinding?.removeActivityResultListener(this)
            this.activityPluginBinding = null
        }

        override fun startIntent(intent: Intent, request: Int) {
            if (activityPluginBinding != null) {
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityPluginBinding?.activity?.startActivityForResult(intent, request)
                } else {
                    finishWithResult(FORM_COULD_NOT_BE_OPEN)
                }
            } else {
                context.startActivity(intent)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private inner class GetContactsTask(
        private val callMethod: String,
        private val getContactResult: Result,
        private val withThumbnails: Boolean,
        private val photoHighResolution: Boolean,
        private val orderByGivenName: Boolean,
        private val localizedLabels: Boolean
    ) : AsyncTask<Any, Void, ArrayList<HashMap<*, *>>>() {

        @TargetApi(Build.VERSION_CODES.ECLAIR)
        override fun doInBackground(vararg params: Any): ArrayList<HashMap<*, *>> {
            val contacts: ArrayList<Contact> = when (callMethod) {
                "openDeviceContactPicker" -> getContactsFrom(getCursor(null, params[0] as String), localizedLabels)
                "getContacts" -> getContactsFrom(getCursor(params[0] as? String, null), localizedLabels)
                "getContactsForPhone" -> getContactsFrom(getCursorForPhone(params[0] as String), localizedLabels)
                "getContactsForEmail" -> getContactsFrom(getCursorForEmail(params[0] as String), localizedLabels)
                else -> return ArrayList()
            }

            if (withThumbnails) {
                for (c in contacts) {
                    val avatar = c.identifier?.let {
                        loadContactPhotoHighRes(
                            it, photoHighResolution, contentResolver ?: return ArrayList()
                        )
                    }
                    c.avatar = avatar ?: ByteArray(0) // To stay backwards-compatible, return empty array rather than null
                }
            }

            if (orderByGivenName) {
                Collections.sort(contacts)
            }

            // Transform the list of contacts to a list of Map
            val contactMaps = ArrayList<HashMap<*, *>>()
            for (c in contacts) {
                contactMaps.add(c.toMap())
            }

            return contactMaps
        }

        override fun onPostExecute(result: ArrayList<HashMap<*, *>>) {
            getContactResult.success(result)
        }
    }

    private fun getCursor(query: String?, rawContactId: String?): Cursor? {
        var selection = "(" + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR " +
                ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR " +
                ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=? OR " +
                ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.RawContacts.ACCOUNT_TYPE + "=?" + ")"
        
        var selectionArgs = arrayOf(
            CommonDataKinds.Note.CONTENT_ITEM_TYPE, 
            Email.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE, 
            StructuredName.CONTENT_ITEM_TYPE, 
            Organization.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE, 
            CommonDataKinds.Event.CONTENT_ITEM_TYPE, 
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        
        if (query != null) {
            selectionArgs = arrayOf("$query%")
            selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?"
        }
        
        if (rawContactId != null) {
            selectionArgs = selectionArgs.plus(rawContactId)
            selection += " AND " + ContactsContract.Data.CONTACT_ID + " =?"
        }
        
        return contentResolver?.query(
            ContactsContract.Data.CONTENT_URI, 
            PROJECTION, 
            selection, 
            selectionArgs, 
            null
        )
    }

    private fun getCursorForPhone(phone: String): Cursor? {
        if (phone.isEmpty()) return null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        val projection = arrayOf(BaseColumns._ID)

        val contactIds = ArrayList<String>()
        contentResolver?.query(uri, projection, null, null, null)?.use { phoneCursor ->
            while (phoneCursor.moveToNext()) {
                contactIds.add(phoneCursor.getString(phoneCursor.getColumnIndex(BaseColumns._ID)))
            }
        }

        if (contactIds.isNotEmpty()) {
            val contactIdsListString = contactIds.toString().replace("[", "(").replace("]", ")")
            val contactSelection = ContactsContract.Data.CONTACT_ID + " IN " + contactIdsListString
            return contentResolver?.query(ContactsContract.Data.CONTENT_URI, PROJECTION, contactSelection, null, null)
        }

        return null
    }

    private fun getCursorForEmail(email: String): Cursor? {
        if (email.isEmpty()) return null
        
        val selection = Email.ADDRESS + " LIKE ?"
        val selectionArgs = arrayOf("%$email%")
        return contentResolver?.query(ContactsContract.Data.CONTENT_URI, PROJECTION, selection, selectionArgs, null)
    }

    private fun getContactsFrom(cursor: Cursor?, localizedLabels: Boolean): ArrayList<Contact> {
        val map = LinkedHashMap<String, Contact>()

        cursor?.use {
            while (it.moveToNext()) {
                val columnIndex = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val contactId = it.getString(columnIndex)

                if (!map.containsKey(contactId)) {
                    map[contactId] = Contact(contactId)
                }
                val contact = map[contactId]!!

                val mimeType = it.getString(it.getColumnIndex(ContactsContract.Data.MIMETYPE))
                contact.displayName = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                contact.androidAccountType = it.getString(it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE))
                contact.androidAccountName = it.getString(it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME))

                // NAMES
                when (mimeType) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        contact.givenName = it.getString(it.getColumnIndex(StructuredName.GIVEN_NAME))
                        contact.middleName = it.getString(it.getColumnIndex(StructuredName.MIDDLE_NAME))
                        contact.familyName = it.getString(it.getColumnIndex(StructuredName.FAMILY_NAME))
                        contact.prefix = it.getString(it.getColumnIndex(StructuredName.PREFIX))
                        contact.suffix = it.getString(it.getColumnIndex(StructuredName.SUFFIX))
                    }
                    // NOTE
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                        contact.note = it.getString(it.getColumnIndex(CommonDataKinds.Note.NOTE))
                    }
                    // PHONES
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val phoneNumber = it.getString(it.getColumnIndex(Phone.NUMBER))
                        if (!TextUtils.isEmpty(phoneNumber)) {
                            val type = it.getInt(it.getColumnIndex(Phone.TYPE))
                            // Add null check for resources
                            val label = resources?.let { res ->
                                Item.getPhoneLabel(res, type, it, localizedLabels)
                            } ?: type.toString() // Fallback to type number if resources is null
                            contact.phones.add(Item(label, phoneNumber, type))
                        }
                    }
                    // EMAILS
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val email = it.getString(it.getColumnIndex(Email.ADDRESS))
                        val type = it.getInt(it.getColumnIndex(Email.TYPE))
                        if (!TextUtils.isEmpty(email)) {
                            // Add null check for resources
                            val label = resources?.let { res ->
                                Item.getEmailLabel(res, type, it, localizedLabels)
                            } ?: type.toString() // Fallback to type number if resources is null
                            contact.emails.add(Item(label, email, type))
                        }
                    }
                    // ORG
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        contact.company = it.getString(it.getColumnIndex(Organization.COMPANY))
                        contact.jobTitle = it.getString(it.getColumnIndex(Organization.TITLE))
                    }
                    // ADDRESSES
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val type = it.getInt(it.getColumnIndex(StructuredPostal.TYPE))
                        val label = PostalAddress.getLabel(resources, type, it, localizedLabels)
                        val street = it.getString(it.getColumnIndex(StructuredPostal.STREET))
                        val city = it.getString(it.getColumnIndex(StructuredPostal.CITY))
                        val postcode = it.getString(it.getColumnIndex(StructuredPostal.POSTCODE))
                        val region = it.getString(it.getColumnIndex(StructuredPostal.REGION))
                        val country = it.getString(it.getColumnIndex(StructuredPostal.COUNTRY))
                        contact.postalAddresses.add(PostalAddress(label, street, city, postcode, region, country, type))
                    }
                    // BIRTHDAY
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val eventType = it.getInt(it.getColumnIndex(CommonDataKinds.Event.TYPE))
                        if (eventType == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                            contact.birthday = it.getString(it.getColumnIndex(CommonDataKinds.Event.START_DATE))
                        }
                    }
                }
            }
        }

        return ArrayList(map.values)
    }

    private fun setAvatarDataForContactIfAvailable(contact: Contact) {
        val contactUri = contact.identifier?.let { ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it.toLong()) }
        val photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
        contentResolver?.query(
            photoUri,
            arrayOf(ContactsContract.Contacts.Photo.PHOTO), 
            null, 
            null, 
            null
        )?.use { avatarCursor ->
            if (avatarCursor.moveToFirst()) {
                val avatar = avatarCursor.getBlob(0)
                contact.avatar = avatar
            }
        }
    }

    private fun getAvatar(contact: Contact, highRes: Boolean, result: Result) {
        GetAvatarsTask(contact, highRes, contentResolver ?: return, result).executeOnExecutor(executor)
    }

    private class GetAvatarsTask(
        private val contact: Contact,
        private val highRes: Boolean,
        private val contentResolver: ContentResolver,
        private val result: Result
    ) : AsyncTask<Void, Void, ByteArray?>() {

        override fun doInBackground(vararg params: Void): ByteArray? {
            // Load avatar for contact identifier
            return contact.identifier?.let { loadContactPhotoHighRes(it, highRes, contentResolver) }
        }

        override fun onPostExecute(avatar: ByteArray?) {
            result.success(avatar)
        }
    }

    private fun addContact(contact: Contact): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        var op = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        ops.add(op.build())

        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.GIVEN_NAME, contact.givenName)
            .withValue(StructuredName.MIDDLE_NAME, contact.middleName)
            .withValue(StructuredName.FAMILY_NAME, contact.familyName)
            .withValue(StructuredName.PREFIX, contact.prefix)
            .withValue(StructuredName.SUFFIX, contact.suffix)
        ops.add(op.build())

        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Note.NOTE, contact.note)
        ops.add(op.build())

        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
        .withValue(Organization.COMPANY, contact.company)
        .withValue(Organization.TITLE, contact.jobTitle)
    ops.add(op.build())

    // Add phones
    for (phone in contact.phones) {
        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, phone.value)
            .withValue(Phone.TYPE, phone.type)
            .withValue(Phone.LABEL, phone.label)
        ops.add(op.build())
    }

    // Add emails
    for (email in contact.emails) {
        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(Email.ADDRESS, email.value)
            .withValue(Email.TYPE, email.type)
            .withValue(Email.LABEL, email.label)
        ops.add(op.build())
    }

    // Add postal addresses
    for (address in contact.postalAddresses) {
        op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
            .withValue(StructuredPostal.TYPE, address.type)
            .withValue(StructuredPostal.STREET, address.street)
            .withValue(StructuredPostal.CITY, address.city)
            .withValue(StructuredPostal.REGION, address.region)
            .withValue(StructuredPostal.POSTCODE, address.postcode)
            .withValue(StructuredPostal.COUNTRY, address.country)
            .withValue(StructuredPostal.LABEL, address.label)
        ops.add(op.build())
    }

    try {
        contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
        return true
    } catch (e: Exception) {
        Log.e(LOG_TAG, e.toString())
        return false
    }
}

private fun deleteContact(contact: Contact): Boolean {
    val ops = ArrayList<ContentProviderOperation>()
    val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.identifier)
    
    ops.add(ContentProviderOperation.newDelete(contactUri).build())
    
    try {
        contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
        return true
    } catch (e: Exception) {
        Log.e(LOG_TAG, e.toString())
        return false
    }
}

private fun updateContact(contact: Contact): Boolean {
    val ops = ArrayList<ContentProviderOperation>()
    val rawContactId = contact.identifier?.let { getRawContactId(it) }
    
    if (rawContactId == null || rawContactId?.toInt() == -1) {
        return false
    }

    // Name
    val nameUri = ContactsContract.Data.CONTENT_URI
    val nameSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val nameSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newUpdate(nameUri)
        .withSelection(nameSelection, nameSelectionArgs)
        .withValue(StructuredName.GIVEN_NAME, contact.givenName)
        .withValue(StructuredName.MIDDLE_NAME, contact.middleName)
        .withValue(StructuredName.FAMILY_NAME, contact.familyName)
        .withValue(StructuredName.PREFIX, contact.prefix)
        .withValue(StructuredName.SUFFIX, contact.suffix)
        .build())

    // Note
    val noteUri = ContactsContract.Data.CONTENT_URI
    val noteSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val noteSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.Note.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newUpdate(noteUri)
        .withSelection(noteSelection, noteSelectionArgs)
        .withValue(CommonDataKinds.Note.NOTE, contact.note)
        .build())

    // Organization
    val orgUri = ContactsContract.Data.CONTENT_URI
    val orgSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val orgSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newUpdate(orgUri)
        .withSelection(orgSelection, orgSelectionArgs)
        .withValue(Organization.COMPANY, contact.company)
        .withValue(Organization.TITLE, contact.jobTitle)
        .build())

    // Phones
    val phoneUri = ContactsContract.Data.CONTENT_URI
    val phoneSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val phoneSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newDelete(phoneUri)
        .withSelection(phoneSelection, phoneSelectionArgs)
        .build())
    
    for (phone in contact.phones) {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, phone.value)
            .withValue(Phone.TYPE, phone.type)
            .withValue(Phone.LABEL, phone.label)
            .build())
    }

    // Emails
    val emailUri = ContactsContract.Data.CONTENT_URI
    val emailSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val emailSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.Email.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newDelete(emailUri)
        .withSelection(emailSelection, emailSelectionArgs)
        .build())
    
    for (email in contact.emails) {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(Email.ADDRESS, email.value)
            .withValue(Email.TYPE, email.type)
            .withValue(Email.LABEL, email.label)
            .build())
    }

    // Postal addresses
    val postalUri = ContactsContract.Data.CONTENT_URI
    val postalSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val postalSelectionArgs = arrayOf(rawContactId.toString(), CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
    
    ops.add(ContentProviderOperation.newDelete(postalUri)
        .withSelection(postalSelection, postalSelectionArgs)
        .build())
    
    for (address in contact.postalAddresses) {
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
            .withValue(StructuredPostal.TYPE, address.type)
            .withValue(StructuredPostal.STREET, address.street)
            .withValue(StructuredPostal.CITY, address.city)
            .withValue(StructuredPostal.REGION, address.region)
            .withValue(StructuredPostal.POSTCODE, address.postcode)
            .withValue(StructuredPostal.COUNTRY, address.country)
            .withValue(StructuredPostal.LABEL, address.label)
            .build())
    }

    try {
        contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
        return true
    } catch (e: Exception) {
        Log.e(LOG_TAG, e.toString())
        return false
    }
}

private fun getRawContactId(contactId: String): Long {
    val projection = arrayOf(ContactsContract.RawContacts._ID)
    val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
    val selectionArgs = arrayOf(contactId)
    
    contentResolver?.query(
        ContactsContract.RawContacts.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndex(ContactsContract.RawContacts._ID))
        }
    }
    
    return -1
}
}