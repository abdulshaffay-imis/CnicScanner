package com.sspa.cnicscanner.entities

/**
 * Data class representing Pakistani CNIC (Computerized National Identity Card) information.
 * This entity holds all the parsed data from CNIC scanning.
 */
data class CnicEntity(
    /** CNIC number (e.g., 12345-6789012-3) */
    var cnic: String = "",
    
    /** Cardholder's name */
    var name: String = "",
    
    /** Father's name */
    var father_name: String = "",
    
    /** Date of birth */
    var date_of_birth: String = "",
    
    /** CNIC issue date */
    var cnic_issue_date: String = "",
    
    /** CNIC expiry date */
    var cnic_expiry: String = "",
    
    /** Gender (Male/Female) */
    var gender: String = "",
    
    /** Country of CNIC holder */
    var cnicHolderCountry: String = "",
    
    /** Present address (typically from back of card) */
    var present_address: String = "",
    
    /** Permanent address (typically from back of card) */
    var permanent_address: String = "",
    
    /** URI string of the front image of CNIC */
    var cnic_front: String? = null,
    
    /** URI string of the back image of CNIC */
    var cnic_back: String? = null
) {
    /**
     * Checks if all essential CNIC fields are populated
     * @return true if all required fields have values
     */
    fun isComplete(): Boolean {
        return cnic.isNotEmpty() &&
                name.isNotEmpty() &&
                date_of_birth.isNotEmpty() &&
                cnic_issue_date.isNotEmpty() &&
                cnic_expiry.isNotEmpty() &&
                cnicHolderCountry.isNotEmpty() &&
                gender.isNotEmpty() &&
                father_name.isNotEmpty()
    }

    override fun toString(): String {
        return "CnicEntity(number='$cnic', name='$name', dob='$date_of_birth', " +
                "issue='$cnic_issue_date', expiry='$cnic_expiry', " +
                "present='${present_address.take(12)}', permanent='${permanent_address.take(12)}', " +
                "frontImage=${cnic_front != null}, backImage=${cnic_back != null})"
    }
}
