package com.example.ui.settings

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PrinterSettingsSection(
    viewModel: RestaurantViewModel
) {
    val context = LocalContext.current
    val receiptSize by viewModel.receiptSize.collectAsState()
    val showLogoOnReceipt by viewModel.showLogoOnReceipt.collectAsState()
    val showTaxOnReceipt by viewModel.showTaxOnReceipt.collectAsState()
    val receiptFooterNote by viewModel.receiptFooterNote.collectAsState()
    val printerAutoCut by viewModel.printerAutoCut.collectAsState()
    val printerFeedLines by viewModel.printerFeedLines.collectAsState()
    val restaurantName by viewModel.restaurantName.collectAsState()
    val restaurantSlogan by viewModel.restaurantSlogan.collectAsState()
    val restaurantPhone by viewModel.restaurantPhone.collectAsState()
    val restaurantAddress by viewModel.restaurantAddress.collectAsState()

    var selectedSize by remember(receiptSize) { mutableStateOf(receiptSize) }
    var showLogo by remember(showLogoOnReceipt) { mutableStateOf(showLogoOnReceipt) }
    var showTax by remember(showTaxOnReceipt) { mutableStateOf(showTaxOnReceipt) }
    var footerNoteInput by remember(receiptFooterNote) { mutableStateOf(receiptFooterNote) }
    var autoCut by remember(printerAutoCut) { mutableStateOf(printerAutoCut) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Thermal POS Printer Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure standard 80mm thermal receipt roll formatting, system print spooler, and receipt display options.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Paper Size Section
        Text(
            text = "Receipt Thermal Paper Size (Standard Roll)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 80mm Card (Standard)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedSize = "80mm" }
                    .testTag("printer_size_80mm"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSize == "80mm") MaterialTheme.colorScheme.primaryContainer 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(
                    width = if (selectedSize == "80mm") 2.dp else 1.dp,
                    color = if (selectedSize == "80mm") MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "80mm (Standard POS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (selectedSize == "80mm") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (selectedSize == "80mm") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selectedSize == "80mm") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Standard wide 80mm thermal receipt roll with optimal layout for dine-in, takeaway, and itemized customer invoices.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Recommended",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 58mm Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedSize = "57mm" }
                    .testTag("printer_size_58mm"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedSize == "57mm" || selectedSize == "58mm") MaterialTheme.colorScheme.primaryContainer 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(
                    width = if (selectedSize == "57mm" || selectedSize == "58mm") 2.dp else 1.dp,
                    color = if (selectedSize == "57mm" || selectedSize == "58mm") MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val is58 = selectedSize == "57mm" || selectedSize == "58mm"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "58mm (Compact)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (is58) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (is58) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (is58) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Compact narrow 58mm mobile thermal roll suited for portable Bluetooth handhelds and mini waist printers.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Gray.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Mobile Handheld",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Receipt Content Options Card
        Text(
            text = "Receipt Content & Layout Options",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Show Logo Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Restaurant Logo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Print restaurant branding graphic at top of receipt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = showLogo,
                        onCheckedChange = { showLogo = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Show Tax Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Tax & VAT Breakdown", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Include tax rate and tax sum line on printed bill", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = showTax,
                        onCheckedChange = { showTax = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Auto-cut switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Paper Feed Spacing (Auto-Tear)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Feed blank margin lines after footer for clean tear-off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoCut,
                        onCheckedChange = { autoCut = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Footer Note text field
                OutlinedTextField(
                    value = footerNoteInput,
                    onValueChange = { footerNoteInput = it },
                    label = { Text("Receipt Footer Note / Greeting") },
                    placeholder = { Text("e.g. Thank you for your business! Please visit again.") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    maxLines = 3
                )
            }
        }

        // Live 80mm Bill Sample Preview
        Text(
            text = "Live 80mm Bill Format Preview",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEFEFEF), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFDCDCDC), RoundedCornerShape(8.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .shadow(3.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "- - - - - - - - - - - - - - - - - - - - -",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
                Text(
                    text = restaurantName.ifBlank { "FOOD CUISINE" }.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                if (restaurantSlogan.isNotBlank()) {
                    Text(text = restaurantSlogan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF444444))
                }
                if (restaurantAddress.isNotBlank()) {
                    Text(text = restaurantAddress, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Color(0xFF666666))
                }
                Text(
                    text = "========================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color.Black
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Order No: ORD-TEST-80", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("[SAMPLE 80mm]", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Text("Type    : Dine-in", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF333333))
                Text("Date    : 2026-08-28 03:30 PM", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF333333))
                Text(
                    text = "----------------------------------------",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color.Black
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1x Chicken Karahi", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                    Text("Rs. 1,450.00", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2x Roghani Naan", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                    Text("Rs. 120.00", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                }
                Text(
                    text = "========================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color.Black
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("NET TOTAL:", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    Text("Rs. 1,570.00", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
                Text(
                    text = "========================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color.Black
                )
                Text(
                    text = footerNoteInput,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF444444),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "- - - - - - - - - - - - - - - - - - - - -",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }

        // Action Buttons Row (Test Print & Save)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Test Print Button
            OutlinedButton(
                onClick = {
                    executeTestPrint80mm(
                        context = context,
                        restaurantName = restaurantName,
                        restaurantSlogan = restaurantSlogan,
                        restaurantAddress = restaurantAddress,
                        restaurantPhone = restaurantPhone,
                        receiptSize = selectedSize,
                        footerNote = footerNoteInput
                    )
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_print_80mm_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Print 80mm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Save Settings Button
            Button(
                onClick = {
                    viewModel.savePrinterSettings(
                        context = context,
                        size = selectedSize,
                        showLogo = showLogo,
                        showTax = showTax,
                        footerNote = footerNoteInput,
                        autoCut = autoCut,
                        feedLines = printerFeedLines
                    )
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_printer_settings_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Printer Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Triggers a sample 80mm test receipt print to verify thermal printer setup.
 */
private fun executeTestPrint80mm(
    context: Context,
    restaurantName: String,
    restaurantSlogan: String,
    restaurantAddress: String,
    restaurantPhone: String,
    receiptSize: String,
    footerNote: String
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Test_Print_80mm"
        val is80mm = receiptSize == "80mm"
        val pageWidth = if (is80mm) "80mm" else "58mm"
        val dateStr = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    @page { size: $pageWidth auto; margin: 0; }
                    * { box-sizing: border-box; -webkit-print-color-adjust: exact; }
                    html, body {
                        width: $pageWidth;
                        max-width: $pageWidth;
                        min-width: $pageWidth;
                        margin: 0 auto;
                        padding: 6mm 4mm;
                        font-family: 'Courier New', Courier, monospace;
                        font-size: 12px;
                        line-height: 1.35;
                        color: #000;
                        background: #fff;
                    }
                    .center { text-align: center; }
                    .bold { font-weight: bold; }
                    .title { font-size: 16px; font-weight: 900; margin: 0 0 2px 0; text-transform: uppercase; }
                    .divider { border-top: 1px dashed #000; margin: 6px 0; }
                    .double-divider { border-top: 2px solid #000; margin: 6px 0; }
                    .flex-row { display: flex; justify-content: space-between; }
                </style>
            </head>
            <body>
                <div class="center title">${restaurantName.ifBlank { "FOOD CUISINE" }}</div>
                ${if (restaurantSlogan.isNotBlank()) """<div class="center" style="font-size: 10px;">$restaurantSlogan</div>""" else ""}
                ${if (restaurantAddress.isNotBlank()) """<div class="center" style="font-size: 9px;">$restaurantAddress</div>""" else ""}
                ${if (restaurantPhone.isNotBlank()) """<div class="center" style="font-size: 9px;">Tel: $restaurantPhone</div>""" else ""}
                
                <div class="double-divider"></div>
                <div class="center bold" style="font-size: 12px;">*** TEST PRINT RECEIPT ($pageWidth) ***</div>
                <div class="divider"></div>
                <div class="flex-row"><span>Date/Time:</span><span>$dateStr</span></div>
                <div class="flex-row"><span>Printer Format:</span><span>$pageWidth Fixed Width</span></div>
                <div class="flex-row"><span>Status:</span><span>ONLINE / READY</span></div>
                
                <div class="divider"></div>
                <div class="flex-row"><span>1x Sample Burger</span><span>Rs. 450.00</span></div>
                <div class="flex-row"><span>1x Fresh Lime</span><span>Rs. 120.00</span></div>
                <div class="double-divider"></div>
                <div class="flex-row bold" style="font-size: 14px;"><span>SAMPLE TOTAL:</span><span>Rs. 570.00</span></div>
                <div class="double-divider"></div>
                
                <div class="center" style="font-size: 10px; margin-top: 6px;">$footerNote</div>
                <div class="center" style="font-size: 8px; color: #555; margin-top: 4px;">*** PRINTER TEST SUCCESSFUL ***</div>
                <div style="height: 12mm;"></div>
            </body>
            </html>
        """.trimIndent()

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val builder = PrintAttributes.Builder()
                builder.setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                printManager.print(jobName, printAdapter, builder.build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
