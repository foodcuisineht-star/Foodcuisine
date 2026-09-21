package com.example.ui.bom

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BomIngredient
import com.example.data.model.MenuItem
import com.example.data.model.RawMaterial
import com.example.ui.theme.*
import com.example.ui.viewmodel.RestaurantViewModel

/**
 * Bill of Materials (BOM) & Recipe Designer Dialog.
 * Mobile-optimized with vertical scroll, keyboard insets support,
 * and buttons featuring icon on top and text at bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDesignerDialog(
    menuItemId: Int,
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val allBomRecipes by viewModel.allBomRecipes.collectAsStateWithLifecycle()

    val targetMenuItem = remember(menuItemId, menuItems) {
        menuItems.firstOrNull { it.id == menuItemId }
    }

    // Active ingredient linkages from database
    val backendIngredients = remember(menuItemId, allBomRecipes) {
        allBomRecipes.filter { it.menuItemId == menuItemId }
    }

    // UI Local Designer Stock Map / Draft State
    val recipeDraftList = remember { mutableStateListOf<BomIngredient>() }
    var recipeYieldSizeText by remember { mutableStateOf("1.0") }

    // Initialize draft from existing recipe if available
    LaunchedEffect(backendIngredients, targetMenuItem) {
        recipeDraftList.clear()
        if (backendIngredients.isNotEmpty()) {
            recipeDraftList.addAll(backendIngredients)
        }
        targetMenuItem?.let {
            recipeYieldSizeText = if (it.recipeYield > 0) {
                if (it.recipeYield % 1.0 == 0.0) it.recipeYield.toInt().toString() else String.format(java.util.Locale.US, "%.2f", it.recipeYield)
            } else "1.0"
        }
    }

    // Input state for linking a new raw material item
    var selectedRawId by remember { mutableStateOf<Int?>(rawMaterials.firstOrNull()?.id) }
    var showRawDropdown by remember { mutableStateOf(false) }
    var ingredientQtyText by remember { mutableStateOf("") }
    var showCopyRecipeDialog by remember { mutableStateOf(false) }

    // Selected raw material details
    val selectedRawMaterial = remember(selectedRawId, rawMaterials) {
        rawMaterials.firstOrNull { it.id == selectedRawId } ?: rawMaterials.firstOrNull()
    }

    // Parsed recipe yield
    val parsedYield = recipeYieldSizeText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0

    // Real-time calculated costs
    val totalBatchCost = recipeDraftList.sumOf { ing ->
        val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
        val costPerUnit = raw?.avgCostPerUnit ?: 0.0
        ing.requiredQuantity * costPerUnit
    }

    val costPerPortion = if (parsedYield > 0) totalBatchCost / parsedYield else 0.0
    val sellingPrice = targetMenuItem?.price ?: 0.0
    val foodCostPercentage = if (sellingPrice > 0) (costPerPortion / sellingPrice) * 100.0 else 0.0
    val grossProfitPerPortion = sellingPrice - costPerPortion

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                // Header Bar (Sticky Top)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Recipe BOM Designer",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Dish: ${targetMenuItem?.name ?: "Unknown"} • Category: ${targetMenuItem?.category ?: "General"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("recipe_dialog_close")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Scrollable Content Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live Financial & Food Cost Economics Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recipe Economics & Food Cost",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Food Cost % Tag
                                val badgeColor = when {
                                    foodCostPercentage <= 0 -> MaterialTheme.colorScheme.outline
                                    foodCostPercentage <= 32 -> DarkGreen
                                    foodCostPercentage <= 45 -> StatusYellow
                                    else -> SoftAlert
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (sellingPrice > 0) "Food Cost: ${String.format(java.util.Locale.US, "%.1f", foodCostPercentage)}%" else "No Price Set",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Cost per portion
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(8.dp)
                                ) {
                                    Text("Cost / Dish", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "Rs. ${String.format(java.util.Locale.US, "%.2f", costPerPortion)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Selling price
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(8.dp)
                                ) {
                                    Text("Selling Price", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "Rs. ${String.format(java.util.Locale.US, "%.2f", sellingPrice)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Gross Profit
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(8.dp)
                                ) {
                                    Text("Profit / Dish", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "Rs. ${String.format(java.util.Locale.US, "%.2f", grossProfitPerPortion)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (grossProfitPerPortion >= 0) DarkGreen else SoftAlert
                                    )
                                }
                            }
                        }
                    }

                    // Standard Recipe Yield Configuration Section (Structured in 3 Clean Distinct Rows)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section Title & Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Recipe Standard Yield",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Batch Size: $recipeYieldSizeText Portions",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // ROW 1: Yield Portions Input & Dish Indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = recipeYieldSizeText,
                                    onValueChange = { recipeYieldSizeText = it },
                                    label = { Text("Standard Yield (Portions / Batch)", fontSize = 10.sp) },
                                    placeholder = { Text("1.0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("recipe_dialog_yield_input"),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Yield Type", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            if (parsedYield == 1.0) "Single Dish" else "Batch",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // ROW 2: Preset Quick Options in a Full Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    1.0 to "1 Dish (1x)",
                                    5.0 to "5x Batch",
                                    10.0 to "10x Batch",
                                    12.0 to "12x (Dozen)",
                                    20.0 to "20x Bulk",
                                    50.0 to "50x Catering"
                                ).forEach { (yVal, label) ->
                                    val isSelected = parsedYield == yVal
                                    OutlinedButton(
                                        onClick = {
                                            recipeYieldSizeText = if (yVal % 1.0 == 0.0) yVal.toInt().toString() else yVal.toString()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // ROW 3: Recipe Standard POS Deduction Ratio & Rule Info
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Standard Deduction Rate: 1/${String.format(java.util.Locale.US, "%.1f", parsedYield)} per dish",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Raw ingredient amounts below define requirements for $recipeYieldSizeText portions. POS automatically scales inventory deductions when sold.",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                // Linked Ingredients Draft List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Linked Raw Materials (${recipeDraftList.size} items)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val otherConfiguredDishes = menuItems.filter { it.id != menuItemId && it.hasBom }
                    if (otherConfiguredDishes.isNotEmpty()) {
                        TextButton(
                            onClick = { showCopyRecipeDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Recipe", fontSize = 11.sp)
                        }
                    }
                }

                // Linked Ingredients List
                if (recipeDraftList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Kitchen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "No raw materials linked yet.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Select ingredients from inventory below to build this recipe.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recipeDraftList.forEach { ing ->
                            val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                            val costPerUnit = raw?.avgCostPerUnit ?: 0.0
                            val lineCost = ing.requiredQuantity * costPerUnit
                            val perDishQty = if (parsedYield > 0) ing.requiredQuantity / parsedYield else 0.0

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ing.rawItemName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (raw != null && raw.quantity <= raw.lowStockThreshold) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(SoftAlert.copy(alpha = 0.15f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        "Low (${raw.quantity} ${raw.unit})",
                                                        fontSize = 8.sp,
                                                        color = SoftAlert,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Req: ${String.format(java.util.Locale.US, "%.2f", ing.requiredQuantity)} ${ing.unit}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (parsedYield > 1.0) {
                                                Text(
                                                    text = "(${String.format(java.util.Locale.US, "%.3f", perDishQty)}/dish)",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "• Rs. ${String.format(java.util.Locale.US, "%.2f", lineCost)}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Adjustment & Delete Controls
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val currentQty = ing.requiredQuantity
                                                val step = if (ing.unit.equals("g", true) || ing.unit.equals("ml", true)) 10.0 else 0.1
                                                val newQty = maxOf(0.01, currentQty - step)
                                                val idx = recipeDraftList.indexOf(ing)
                                                if (idx >= 0) {
                                                    recipeDraftList[idx] = ing.copy(requiredQuantity = newQty)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                val currentQty = ing.requiredQuantity
                                                val step = if (ing.unit.equals("g", true) || ing.unit.equals("ml", true)) 10.0 else 0.1
                                                val newQty = currentQty + step
                                                val idx = recipeDraftList.indexOf(ing)
                                                if (idx >= 0) {
                                                    recipeDraftList[idx] = ing.copy(requiredQuantity = newQty)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { recipeDraftList.remove(ing) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("recipe_dialog_remove_ingredient_${ing.rawItemId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove ingredient",
                                                tint = SoftAlert,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Link New Raw Material from Inventory Tool Block
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Link Raw Material from Inventory",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (rawMaterials.isEmpty()) {
                            Text(
                                "Raw materials inventory is empty. Please register raw ingredients in the Inventory tab first.",
                                fontSize = 11.sp,
                                color = SoftAlert
                            )
                        } else {
                            // Row 1: Dropdown Selector for Raw Material
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .clickable { showRawDropdown = true }
                                        .padding(horizontal = 10.dp)
                                        .testTag("recipe_raw_material_dropdown_trigger"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedRawMaterial?.let { "${it.name} (${it.unit}) • Rs. ${String.format(java.util.Locale.US, "%.2f", it.avgCostPerUnit)}/${it.unit}" }
                                                ?: "Select raw material...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showRawDropdown,
                                    onDismissRequest = { showRawDropdown = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .heightIn(max = 230.dp)
                                        .testTag("recipe_raw_material_dropdown_menu")
                                ) {
                                    rawMaterials.forEach { raw ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(raw.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            "Stock: ${raw.quantity} ${raw.unit} • Rs. ${String.format(java.util.Locale.US, "%.2f", raw.avgCostPerUnit)}/${raw.unit}",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            raw.unit,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedRawId = raw.id
                                                showRawDropdown = false
                                            },
                                            modifier = Modifier.testTag("recipe_raw_material_item_${raw.id}")
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 2: Required Quantity Input & Add Button (Icon Top, Text Bottom)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = ingredientQtyText,
                                    onValueChange = { ingredientQtyText = it },
                                    label = { Text("Quantity (${selectedRawMaterial?.unit ?: "unit"})", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. 0.25") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("ingredient_qty_input"),
                                    singleLine = true
                                )

                                val parsedQty = ingredientQtyText.toDoubleOrNull() ?: 0.0
                                Button(
                                    onClick = {
                                        val activeRaw = selectedRawMaterial ?: return@Button
                                        val existingIndex = recipeDraftList.indexOfFirst { it.rawItemId == activeRaw.id }
                                        if (existingIndex >= 0) {
                                            val existing = recipeDraftList[existingIndex]
                                            recipeDraftList[existingIndex] = existing.copy(
                                                requiredQuantity = existing.requiredQuantity + parsedQty
                                            )
                                        } else {
                                            recipeDraftList.add(
                                                BomIngredient(
                                                    menuItemId = menuItemId,
                                                    rawItemId = activeRaw.id,
                                                    rawItemName = activeRaw.name,
                                                    requiredQuantity = parsedQty,
                                                    unit = activeRaw.unit
                                                )
                                            )
                                        }
                                        ingredientQtyText = ""
                                    },
                                    enabled = parsedQty > 0 && selectedRawMaterial != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("recipe_dialog_add_to_draft_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Link Item",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Dedicated Sticky Bottom Action Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .testTag("recipe_dialog_bottom_close_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Close",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val yieldDouble = recipeYieldSizeText.toDoubleOrNull() ?: 1.0
                                viewModel.saveRecipe(menuItemId, recipeDraftList.toList(), yieldDouble)
                                onDismiss()
                            },
                            enabled = parsedYield > 0,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(32.dp)
                                .testTag("recipe_dialog_save_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save Recipe",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Save Recipe",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Modal to copy recipe from another dish
    if (showCopyRecipeDialog) {
        val otherDishesWithBom = menuItems.filter { it.id != menuItemId && it.hasBom }
        AlertDialog(
            onDismissRequest = { showCopyRecipeDialog = false },
            title = { Text("Copy Recipe Structure", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select a dish to copy its recipe ingredients and yield size:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(otherDishesWithBom) { otherDish ->
                            val otherIngs = allBomRecipes.filter { it.menuItemId == otherDish.id }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        recipeDraftList.clear()
                                        recipeDraftList.addAll(
                                            otherIngs.map { it.copy(id = 0, menuItemId = menuItemId) }
                                        )
                                        recipeYieldSizeText = if (otherDish.recipeYield % 1.0 == 0.0) otherDish.recipeYield.toInt().toString() else otherDish.recipeYield.toString()
                                        showCopyRecipeDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(otherDish.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${otherIngs.size} ingredients • Yield: ${otherDish.recipeYield.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCopyRecipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
