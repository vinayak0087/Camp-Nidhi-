package com.example.domain

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.BillExtraction
import com.example.data.model.Camp
import com.example.data.model.ExpenseRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.abs

data class AiBillOcrResult(
    val vendorName: String = "",
    val billNumber: String = "",
    val billDate: String = "",
    val gstin: String = "",
    val itemDescription: String = "",
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val subtotal: Double? = null,
    val gstAmount: Double? = null,
    val discount: Double? = null,
    val grandTotal: Double? = null,
    val paymentInfo: String = "",
    val confidenceLevel: String = "MEDIUM",
    val uncertainFields: List<String> = emptyList(),
    val notes: String = ""
)

class BillScannerAiService {

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "vendorName" to Schema.string(description = "Shop or vendor name on the bill", nullable = true),
            "billNumber" to Schema.string(description = "Invoice or bill number", nullable = true),
            "billDate" to Schema.string(description = "Date shown on bill e.g. DD/MM/YYYY", nullable = true),
            "gstin" to Schema.string(description = "15-char Indian GSTIN if visible", nullable = true),
            "itemDescription" to Schema.string(description = "Concise summary of billed items", nullable = true),
            "quantity" to Schema.double(description = "Total or primary item quantity", nullable = true),
            "unitPrice" to Schema.double(description = "Unit price if single/clear item", nullable = true),
            "subtotal" to Schema.double(description = "Subtotal before GST and discount", nullable = true),
            "gstAmount" to Schema.double(description = "Total GST or tax amount", nullable = true),
            "discount" to Schema.double(description = "Discount amount if present", nullable = true),
            "grandTotal" to Schema.double(description = "Final total payable on bill", nullable = true),
            "paymentInfo" to Schema.string(description = "Payment mode/UPI/ref printed on bill", nullable = true),
            "confidenceLevel" to Schema.enumeration(
                values = listOf("HIGH", "MEDIUM", "LOW"),
                description = "Overall OCR confidence"
            ),
            "uncertainFields" to Schema.array(
                items = Schema.string(),
                description = "Names of fields that are blurry, handwritten, or missing"
            ),
            "notes" to Schema.string(description = "Short observations on legibility without guessing numbers", nullable = true)
        ),
        optionalProperties = listOf(
            "vendorName", "billNumber", "billDate", "gstin", "itemDescription",
            "quantity", "unitPrice", "subtotal", "gstAmount", "discount",
            "grandTotal", "paymentInfo", "notes"
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun extractBillDetails(billBitmap: Bitmap): Result<AiBillOcrResult> {
        return try {
            val promptText = """
                You are a financial audit OCR assistant for CampNidhi (India).
                Extract receipt/invoice details from this bill image strictly as JSON.
                CRITICAL RULES:
                1. NEVER invent, guess, or fabricate missing financial amounts, dates, or bill numbers.
                2. If a field is not clearly readable or absent, return null (or empty string) and add that field name to "uncertainFields".
                3. Express all currency values as plain numbers in INR (without ₹ symbol).
                4. Set confidenceLevel to LOW if handwritten/blurry or key totals are uncertain.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(billBitmap)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI OCR returned an empty response."))

            val obj = JSONObject(rawJson)
            val uncertainArr = obj.optJSONArray("uncertainFields")
            val uncertain = if (uncertainArr != null) {
                (0 until uncertainArr.length()).mapNotNull { uncertainArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else emptyList()

            Result.success(
                AiBillOcrResult(
                    vendorName = obj.optStringOrEmpty("vendorName"),
                    billNumber = obj.optStringOrEmpty("billNumber"),
                    billDate = obj.optStringOrEmpty("billDate"),
                    gstin = obj.optStringOrEmpty("gstin"),
                    itemDescription = obj.optStringOrEmpty("itemDescription"),
                    quantity = obj.optDoubleOrNull("quantity"),
                    unitPrice = obj.optDoubleOrNull("unitPrice"),
                    subtotal = obj.optDoubleOrNull("subtotal"),
                    gstAmount = obj.optDoubleOrNull("gstAmount"),
                    discount = obj.optDoubleOrNull("discount"),
                    grandTotal = obj.optDoubleOrNull("grandTotal"),
                    paymentInfo = obj.optStringOrEmpty("paymentInfo"),
                    confidenceLevel = obj.optString("confidenceLevel", "MEDIUM"),
                    uncertainFields = uncertain,
                    notes = obj.optStringOrEmpty("notes")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bill OCR timed out", e)
            Result.failure(IllegalStateException("Bill AI extraction timed out after 60 seconds. Please verify manually or retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bill AI extraction failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Bill AI extraction failed."))
        }
    }

    private fun JSONObject.optStringOrEmpty(key: String): String {
        if (isNull(key)) return ""
        val s = optString(key, "").trim()
        return if (s.equals("null", ignoreCase = true)) "" else s
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private companion object {
        const val TAG = "BillScannerAi"
    }
}

object BillAnomalyDetector {

    fun analyze(
        vendorName: String,
        billNumber: String,
        billDate: String,
        quantity: Double?,
        unitPrice: Double?,
        subtotal: Double?,
        gstAmount: Double?,
        discount: Double?,
        grandTotal: Double?,
        confidenceLevel: String,
        uncertainFields: List<String>,
        fileHashSha256: String,
        camp: Camp?,
        existingBills: List<BillExtraction>,
        existingExpenses: List<ExpenseRecord>,
        excludeBillId: String = ""
    ): List<String> {
        val warnings = mutableListOf<String>()

        if (confidenceLevel.equals("LOW", ignoreCase = true) || uncertainFields.isNotEmpty()) {
            val fields = if (uncertainFields.isEmpty()) "general legibility" else uncertainFields.joinToString(", ")
            warnings += "Low-confidence OCR detected ($fields). Please verify carefully."
        }

        val missing = mutableListOf<String>()
        if (vendorName.isBlank()) missing += "Vendor Name"
        if (billNumber.isBlank()) missing += "Bill Number"
        if (billDate.isBlank()) missing += "Bill Date"
        if (grandTotal == null || grandTotal <= 0.0) missing += "Grand Total"
        if (missing.isNotEmpty()) {
            warnings += "Missing information: ${missing.joinToString(", ")}."
        }

        if (quantity != null && unitPrice != null && subtotal != null) {
            val expectedSub = quantity * unitPrice
            if (abs(expectedSub - subtotal) > 2.0) {
                warnings += "Arithmetic mismatch: Qty ($quantity) × Unit Price (₹$unitPrice) = ₹${"%.2f".format(expectedSub)}, differs from Subtotal ₹${"%.2f".format(subtotal)}."
            }
        }

        if (subtotal != null && grandTotal != null) {
            val expectedGrand = subtotal + (gstAmount ?: 0.0) - (discount ?: 0.0)
            if (abs(expectedGrand - grandTotal) > 2.0) {
                warnings += "Arithmetic mismatch: Subtotal + GST - Discount = ₹${"%.2f".format(expectedGrand)}, differs from Grand Total ₹${"%.2f".format(grandTotal)}."
            }
        }

        if (fileHashSha256.isNotBlank()) {
            val hashDup = existingBills.firstOrNull {
                it.id != excludeBillId && it.fileHashSha256 == fileHashSha256
            }
            if (hashDup != null) {
                warnings += "Duplicate document file detected (matches ${hashDup.billVerificationId})."
            }
        }

        if (billNumber.isNotBlank()) {
            val invDup = existingBills.firstOrNull {
                it.id != excludeBillId &&
                    it.billNumber.equals(billNumber.trim(), ignoreCase = true) &&
                    (vendorName.isBlank() || it.vendorName.equals(vendorName.trim(), ignoreCase = true))
            }
            if (invDup != null) {
                warnings += "Duplicate invoice number '${billNumber.trim()}' already exists (${invDup.billVerificationId})."
            }
        }

        if (grandTotal != null && vendorName.isNotBlank()) {
            val nearDup = existingBills.firstOrNull {
                it.id != excludeBillId &&
                    it.vendorName.equals(vendorName.trim(), ignoreCase = true) &&
                    it.grandTotal != null && abs(it.grandTotal - grandTotal) < 0.01 &&
                    (billDate.isBlank() || it.billDate == billDate.trim())
            }
            if (nearDup != null) {
                warnings += "Potentially duplicate bill: same vendor & amount (₹${grandTotal.toLong()}) as ${nearDup.billVerificationId}."
            }
        }

        if (grandTotal != null && camp != null) {
            if (camp.approvalLimit > 0 && grandTotal > camp.approvalLimit) {
                warnings += "Unusual/high expense amount (₹${grandTotal.toLong()}) exceeds camp approval limit (₹${camp.approvalLimit.toLong()})."
            } else if (existingExpenses.isNotEmpty()) {
                val avg = existingExpenses.map { it.amount }.average()
                if (avg > 0 && grandTotal > avg * 3.0) {
                    warnings += "Unusual amount: ₹${grandTotal.toLong()} is >3× average camp expense (₹${avg.toLong()})."
                }
            }
        }

        return warnings
    }
}
