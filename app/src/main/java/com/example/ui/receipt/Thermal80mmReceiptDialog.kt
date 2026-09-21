package com.example.ui.receipt

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Order
import com.example.data.model.OrderItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Renders an authentic, fixed 80mm thermal receipt dialog and provides high-fidelity
 * fixed 80mm ESC/POS HTML print generation.
 */
@Composable
fun Thermal80mmReceiptDialog(
    order: Order,
    orderItems: List<OrderItem>,
    restaurantName: String,
    restaurantSlogan: String,
    restaurantPhone: String,
    restaurantAddress: String,
    logoBase64: String,
    receiptSize: String = "80mm",
    showLogo: Boolean = true,
    showTax: Boolean = true,
    footerNote: String = "Thank you for your business! Please visit again.",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(order.orderDate) {
        SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(order.orderDate))
    }

    val logoBitmap = remember(logoBase64) {
        if (logoBase64.isNotBlank()) {
            try {
                val decoded = Base64.decode(logoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val receiptText = remember(order, orderItems, restaurantName, footerNote) {
        buildReceiptPlainText(
            order = order,
            orderItems = orderItems,
            restaurantName = restaurantName,
            restaurantPhone = restaurantPhone,
            restaurantAddress = restaurantAddress,
            footerNote = footerNote
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Header with 80mm badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "80mm Bill Receipt View",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Fixed Standard 80mm Thermal Page",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Scrollable 80mm Paper Preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE8E8E8))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Fixed 80mm width paper container (approx 310dp)
                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .shadow(4.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Paper Top Tear Indicator
                        Text(
                            text = "- - - - - - - - - - - - - - - - - - - - - - - -",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Logo if present & enabled
                        if (showLogo && logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap,
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Restaurant Header
                        Text(
                            text = restaurantName.ifBlank { "FOOD CUISINE" }.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        if (restaurantSlogan.isNotBlank()) {
                            Text(
                                text = restaurantSlogan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF444444),
                                textAlign = TextAlign.Center
                            )
                        }
                        if (restaurantAddress.isNotBlank()) {
                            Text(
                                text = restaurantAddress,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFF555555),
                                textAlign = TextAlign.Center
                            )
                        }
                        if (restaurantPhone.isNotBlank()) {
                            Text(
                                text = "Tel: $restaurantPhone",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF444444),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "POS SALES BILL (80mm)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.Black
                        )

                        Text(
                            text = "========================================",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Meta details
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Order No : ${order.orderNumber}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text("[${order.status.uppercase()}]", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Text(
                                text = "Type     : ${order.type}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = "Date     : $dateStr",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = "Customer : ${if (order.customerName.isNotBlank()) order.customerName else "Walk-in"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF333333)
                            )
                            if (order.customerPhone.isNotBlank()) {
                                Text(
                                    text = "Phone    : ${order.customerPhone}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF333333)
                                )
                            }
                            if (order.type == "Delivery" && order.riderName.isNotBlank()) {
                                Text(
                                    text = "Rider    : ${order.riderName} (${order.riderPhone})",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF333333)
                                )
                            }
                        }

                        Text(
                            text = "----------------------------------------",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Items Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("QTY  ITEM DESCRIPTION", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text("TOTAL (Rs)", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        Text(
                            text = "----------------------------------------",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Items list
                        orderItems.forEach { item ->
                            val itemTotal = String.format(Locale.getDefault(), "%.2f", item.totalAmount)
                            val unitPrice = String.format(Locale.getDefault(), "%.2f", item.unitPrice)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.quantity.toInt()}x ${item.menuItemName}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = itemTotal,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                                Text(
                                    text = "    @ Rs. $unitPrice",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        Text(
                            text = "----------------------------------------",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Calculations
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                                Text("Rs. ${String.format(Locale.getDefault(), "%.2f", order.subtotal)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                            }
                            if (showTax && order.taxAmount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tax (VAT/GST):", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF444444))
                                    Text("Rs. ${String.format(Locale.getDefault(), "%.2f", order.taxAmount)}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF444444))
                                }
                            }
                            if (order.riderCharges > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Delivery Charges:", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF444444))
                                    Text("Rs. ${String.format(Locale.getDefault(), "%.2f", order.riderCharges)}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF444444))
                                }
                            }
                        }

                        Text(
                            text = "========================================",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Grand Total
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET TOTAL:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                            Text(
                                text = "Rs. ${String.format(Locale.getDefault(), "%.2f", order.totalAmount)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }

                        Text(
                            text = "========================================",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black,
                            maxLines = 1
                        )

                        // Payment info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Payment Mode : ${order.paymentMethod.ifBlank { "Cash" }}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                            Text("Status: ${order.status}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Footer message
                        Text(
                            text = footerNote,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF444444),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "*** 80mm Thermal Receipt End ***",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Bottom Paper Tear Line
                        Text(
                            text = "- - - - - - - - - - - - - - - - - - - - - - - -",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Action Bar (Print 80mm, WhatsApp Share, Close)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                printFixed80mmHtmlReceipt(
                                    context = context,
                                    order = order,
                                    orderItems = orderItems,
                                    restaurantName = restaurantName,
                                    restaurantSlogan = restaurantSlogan,
                                    restaurantPhone = restaurantPhone,
                                    restaurantAddress = restaurantAddress,
                                    logoBase64 = logoBase64,
                                    receiptSize = receiptSize,
                                    showLogo = showLogo,
                                    showTax = showTax,
                                    footerNote = footerNote
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("print_80mm_bill_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Print 80mm",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Print 80mm Bill", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, receiptText)
                                    type = "text/plain"
                                    `package` = "com.whatsapp"
                                }
                                try {
                                    context.startActivity(sendIntent)
                                } catch (e: Exception) {
                                    val shareIntent = Intent.createChooser(
                                        Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, receiptText)
                                            type = "text/plain"
                                        },
                                        "Share Receipt Bill"
                                    )
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "WhatsApp",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("WhatsApp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Close", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generates an authentic plain-text receipt for WhatsApp and sharing.
 */
fun buildReceiptPlainText(
    order: Order,
    orderItems: List<OrderItem>,
    restaurantName: String,
    restaurantPhone: String,
    restaurantAddress: String,
    footerNote: String
): String {
    val builder = StringBuilder()
    builder.append("================================\n")
    builder.append("       ${restaurantName.ifBlank { "FOOD CUISINE" }.uppercase()}       \n")
    if (restaurantAddress.isNotBlank()) builder.append("  $restaurantAddress\n")
    if (restaurantPhone.isNotBlank()) builder.append("  Tel: $restaurantPhone\n")
    builder.append("================================\n")
    builder.append("POS SALES BILL (80mm Standard)\n")
    builder.append("Order No : ${order.orderNumber}\n")
    builder.append("Status   : ${order.status}\n")
    builder.append("Type     : ${order.type}\n")
    val dateStr = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(order.orderDate))
    builder.append("Date     : $dateStr\n")
    builder.append("Customer : ${if (order.customerName.isNotBlank()) order.customerName else "Walk-in"}\n")
    if (order.customerPhone.isNotBlank()) builder.append("Phone    : ${order.customerPhone}\n")
    if (order.riderName.isNotBlank()) builder.append("Rider    : ${order.riderName} (${order.riderPhone})\n")
    builder.append("--------------------------------\n")
    builder.append("QTY  ITEM                 TOTAL \n")
    builder.append("--------------------------------\n")
    orderItems.forEach { item ->
        val total = String.format(Locale.getDefault(), "%.2f", item.totalAmount)
        val unit = String.format(Locale.getDefault(), "%.2f", item.unitPrice)
        builder.append("${item.quantity.toInt()}x ${item.menuItemName}\n")
        builder.append("   @ Rs. $unit -> Rs. $total\n")
    }
    builder.append("--------------------------------\n")
    builder.append("Subtotal : Rs. ${String.format(Locale.getDefault(), "%.2f", order.subtotal)}\n")
    if (order.taxAmount > 0) {
        builder.append("Tax (VAT): Rs. ${String.format(Locale.getDefault(), "%.2f", order.taxAmount)}\n")
    }
    if (order.riderCharges > 0) {
        builder.append("Delivery : Rs. ${String.format(Locale.getDefault(), "%.2f", order.riderCharges)}\n")
    }
    builder.append("================================\n")
    builder.append("NET TOTAL: Rs. ${String.format(Locale.getDefault(), "%.2f", order.totalAmount)}\n")
    builder.append("================================\n")
    builder.append("Payment  : ${order.paymentMethod.ifBlank { "Cash" }}\n")
    builder.append("\n$footerNote\n")
    builder.append("================================\n")
    return builder.toString()
}

/**
 * Prints a fixed 80mm or 58mm thermal receipt page via Android PrintManager & WebView.
 */
fun printFixed80mmHtmlReceipt(
    context: Context,
    order: Order,
    orderItems: List<OrderItem>,
    restaurantName: String,
    restaurantSlogan: String,
    restaurantPhone: String,
    restaurantAddress: String,
    logoBase64: String,
    receiptSize: String = "80mm",
    showLogo: Boolean = true,
    showTax: Boolean = true,
    footerNote: String = "Thank you for your business! Please visit again."
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "Receipt_${order.orderNumber}_80mm"
        val is80mm = receiptSize == "80mm"
        val pageWidth = if (is80mm) "80mm" else "58mm"
        val bodyPadding = if (is80mm) "6mm 4mm" else "3mm 2mm"
        val fontSize = if (is80mm) "12px" else "10.5px"
        val titleSize = if (is80mm) "16px" else "13px"

        val dateStr = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(order.orderDate))

        val logoHtml = if (showLogo && logoBase64.isNotBlank()) {
            """<div class="center" style="margin-bottom: 4px;"><img src="data:image/png;base64,$logoBase64" style="max-height: 48px; max-width: 48px; border-radius: 4px;"/></div>"""
        } else ""

        val itemsHtml = orderItems.joinToString("") { item ->
            """
            <tr>
                <td style="width: 15%; font-weight: bold;">${item.quantity.toInt()}x</td>
                <td style="width: 55%;">${item.menuItemName}<br/><span style="font-size: 9px; color: #555;">@ Rs. ${String.format(Locale.getDefault(), "%.2f", item.unitPrice)}</span></td>
                <td style="width: 30%; text-align: right; font-weight: bold;">Rs. ${String.format(Locale.getDefault(), "%.2f", item.totalAmount)}</td>
            </tr>
            """
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    @page {
                        size: $pageWidth auto;
                        margin: 0;
                    }
                    * {
                        box-sizing: border-box;
                        -webkit-print-color-adjust: exact;
                    }
                    html, body {
                        width: $pageWidth;
                        max-width: $pageWidth;
                        min-width: $pageWidth;
                        margin: 0 auto;
                        padding: $bodyPadding;
                        font-family: 'Courier New', Courier, monospace, sans-serif;
                        font-size: $fontSize;
                        line-height: 1.3;
                        color: #000;
                        background: #fff;
                    }
                    .center { text-align: center; }
                    .right { text-align: right; }
                    .bold { font-weight: bold; }
                    .title { font-size: $titleSize; font-weight: 900; margin: 0 0 2px 0; text-transform: uppercase; }
                    .subtitle { font-size: 10px; margin: 0 0 2px 0; color: #333; }
                    .divider { border-top: 1px dashed #000; margin: 5px 0; }
                    .double-divider { border-top: 2px solid #000; margin: 5px 0; }
                    .flex-row { display: flex; justify-content: space-between; align-items: baseline; }
                    table { width: 100%; border-collapse: collapse; margin: 4px 0; }
                    th { font-size: 10px; text-align: left; border-bottom: 1px dashed #000; padding: 2px 0; }
                    td { font-size: $fontSize; padding: 2px 0; vertical-align: top; }
                    .status-box { display: inline-block; border: 1px solid #000; padding: 1px 6px; font-size: 10px; font-weight: bold; border-radius: 2px; }
                    .total-row { font-size: 14px; font-weight: 900; }
                    .footer { font-size: 10px; text-align: center; margin-top: 6px; }
                    .feed-spacer { height: 12mm; }
                </style>
            </head>
            <body>
                $logoHtml
                <div class="center title">${restaurantName.ifBlank { "FOOD CUISINE" }}</div>
                ${if (restaurantSlogan.isNotBlank()) """<div class="center subtitle">$restaurantSlogan</div>""" else ""}
                ${if (restaurantAddress.isNotBlank()) """<div class="center subtitle">$restaurantAddress</div>""" else ""}
                ${if (restaurantPhone.isNotBlank()) """<div class="center subtitle">Tel: $restaurantPhone</div>""" else ""}
                <div class="center bold" style="font-size: 11px; margin-top: 3px;">POS SALES RECEIPT ($pageWidth)</div>
                
                <div class="double-divider"></div>
                <div class="flex-row">
                    <span><b>Order No:</b> ${order.orderNumber}</span>
                    <span class="status-box">${order.status.uppercase()}</span>
                </div>
                <div class="flex-row">
                    <span><b>Type:</b> ${order.type}</span>
                </div>
                <div class="flex-row">
                    <span><b>Date:</b> $dateStr</span>
                </div>
                <div class="flex-row">
                    <span><b>Customer:</b> ${if (order.customerName.isNotBlank()) order.customerName else "Walk-in"}</span>
                    ${if (order.customerPhone.isNotBlank()) """<span>${order.customerPhone}</span>""" else ""}
                </div>
                ${if (order.type == "Delivery" && order.riderName.isNotBlank()) """<div class="flex-row"><span><b>Rider:</b> ${order.riderName}</span><span>${order.riderPhone}</span></div>""" else ""}

                <div class="divider"></div>
                <table>
                    <thead>
                        <tr>
                            <th style="width: 15%;">QTY</th>
                            <th style="width: 55%;">ITEM</th>
                            <th style="width: 30%; text-align: right;">TOTAL</th>
                        </tr>
                    </thead>
                    <tbody>
                        $itemsHtml
                    </tbody>
                </table>

                <div class="divider"></div>
                <div class="flex-row"><span>Subtotal:</span><span>Rs. ${String.format(Locale.getDefault(), "%.2f", order.subtotal)}</span></div>
                ${if (showTax && order.taxAmount > 0) """<div class="flex-row"><span>Tax (VAT/GST):</span><span>Rs. ${String.format(Locale.getDefault(), "%.2f", order.taxAmount)}</span></div>""" else ""}
                ${if (order.riderCharges > 0) """<div class="flex-row"><span>Delivery Fee:</span><span>Rs. ${String.format(Locale.getDefault(), "%.2f", order.riderCharges)}</span></div>""" else ""}
                
                <div class="double-divider"></div>
                <div class="flex-row total-row">
                    <span>NET TOTAL:</span>
                    <span>Rs. ${String.format(Locale.getDefault(), "%.2f", order.totalAmount)}</span>
                </div>
                <div class="double-divider"></div>
                <div class="flex-row" style="font-size: 10px;">
                    <span>Payment Mode: <b>${order.paymentMethod.ifBlank { "Cash" }}</b></span>
                    <span>Status: <b>${order.status}</b></span>
                </div>

                <div class="footer">$footerNote</div>
                <div class="center" style="font-size: 8px; color: #777; margin-top: 4px;">*** END OF RECEIPT ***</div>
                <div class="feed-spacer"></div>
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
