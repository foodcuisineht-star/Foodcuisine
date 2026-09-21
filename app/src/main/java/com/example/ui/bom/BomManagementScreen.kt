package com.example.ui.bom

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BomIngredient
import com.example.data.model.MenuItem
import com.example.data.model.RawMaterial
import com.example.ui.theme.*
import com.example.ui.viewmodel.RestaurantViewModel

/**
 * Dedicated Bill of Materials (BOM) Management Screen.
 * Fully optimized for mobile scrolling and responsive layouts.
 * Buttons feature Icon on top and Text at bottom for superior mobile touch targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomManagementScreen(
    viewModel: RestaurantViewModel,
    activeRole: String,
    onConfigureRecipeClick: (Int) -> Unit,
    onAddMenuClick: () -> Unit,
    onProduceItemClick: ((Int) -> Unit)? = null
) {
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val allBomRecipes by viewModel.allBomRecipes.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") } // "All", "Configured", "Missing"
    var itemToDeleteRecipe by remember { mutableStateOf<MenuItem?>(null) }

    // Categories available
    val categories = remember(menuItems) {
        listOf("All") + menuItems.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    // Filtered Menu Items
    val filteredMenuItems = remember(menuItems, allBomRecipes, searchQuery, selectedCategoryFilter, selectedStatusFilter) {
        menuItems.filter { item ->
            val hasRecipe = item.hasBom || allBomRecipes.any { it.menuItemId == item.id }
            val matchesCategory = selectedCategoryFilter == "All" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesStatus = when (selectedStatusFilter) {
                "Configured" -> hasRecipe
                "Missing" -> !hasRecipe
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    allBomRecipes.any { it.menuItemId == item.id && it.rawItemName.contains(searchQuery, ignoreCase = true) }

            matchesCategory && matchesStatus && matchesSearch
        }
    }

    // High-level BOM Analytics
    val totalDishesCount = menuItems.size
    val configuredDishesCount = menuItems.count { it.hasBom || allBomRecipes.any { b -> b.menuItemId == it.id } }
    val missingDishesCount = totalDishesCount - configuredDishesCount
    val bomCoveragePercent = if (totalDishesCount > 0) (configuredDishesCount.toDouble() / totalDishesCount) * 100.0 else 0.0

    // Average Food Cost % across configured dishes
    val avgFoodCostPercent = remember(menuItems, allBomRecipes, rawMaterials) {
        val configuredItems = menuItems.filter { it.hasBom || allBomRecipes.any { b -> b.menuItemId == it.id } }
        if (configuredItems.isEmpty()) 0.0
        else {
            val percentages = configuredItems.mapNotNull { item ->
                val ingredients = allBomRecipes.filter { it.menuItemId == item.id }
                if (ingredients.isEmpty() || item.price <= 0) null
                else {
                    val batchCost = ingredients.sumOf { ing ->
                        val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                        ing.requiredQuantity * (raw?.avgCostPerUnit ?: 0.0)
                    }
                    val yieldVal = if (item.recipeYield > 0) item.recipeYield else 1.0
                    val costPerDish = batchCost / yieldVal
                    (costPerDish / item.price) * 100.0
                }
            }
            if (percentages.isNotEmpty()) percentages.average() else 0.0
        }
    }

    // Low stock raw items
    val lowStockItems = remember(rawMaterials) {
        rawMaterials.filter { it.quantity <= it.lowStockThreshold }
    }

    // Unified scrollable container for flawless mobile experience
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp)
    ) {
        // 1. Top Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            text = "Bill of Materials (BOM)",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Recipe engineering, raw material costs & food margins",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeRole == "Admin") {
                    Button(
                        onClick = onAddMenuClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("bom_add_menu_item_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Add Dish",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Summary Metric Cards (Responsive 2x2 Grid for Mobile)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Dishes
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedStatusFilter = "All" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedStatusFilter == "All") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Dishes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalDishesCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("In restaurant menu", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // BOM Linked
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedStatusFilter = if (selectedStatusFilter == "Configured") "All" else "Configured" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedStatusFilter == "Configured") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("BOM Linked", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$configuredDishesCount / $totalDishesCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                            Text("${String.format(java.util.Locale.US, "%.0f", bomCoveragePercent)}% configured", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = DarkGreen)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Needs BOM
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedStatusFilter = if (selectedStatusFilter == "Missing") "All" else "Missing" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedStatusFilter == "Missing") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (missingDishesCount > 0) SoftAlert.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Needs BOM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$missingDishesCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (missingDishesCount > 0) SoftAlert else DarkGreen)
                            Text(if (missingDishesCount > 0) "Needs recipe" else "All configured!", fontSize = 9.sp, color = if (missingDishesCount > 0) SoftAlert else DarkGreen)
                        }
                    }

                    // Avg Food Cost %
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Avg Food Cost", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (configuredDishesCount > 0) "${String.format(java.util.Locale.US, "%.1f", avgFoodCostPercent)}%" else "—",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    avgFoodCostPercent <= 32 -> DarkGreen
                                    avgFoodCostPercent <= 45 -> StatusYellow
                                    else -> SoftAlert
                                }
                            )
                            Text("Target: <35%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 3. Low Safety Stock Warning Card
        if (lowStockItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${lowStockItems.size} Raw Ingredients Below Safety Stock",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Low raw stock limits dish production capacity:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lowStockItems.forEach { raw ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Column {
                                        Text(raw.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("Stock: ${raw.quantity} ${raw.unit} (Min: ${raw.lowStockThreshold})", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Search and Filter Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search dish or raw ingredient...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bom_search_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Filter Chips (Status & Categories)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "All",
                            onClick = { selectedStatusFilter = "All" },
                            label = { Text("All (${menuItems.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "Configured",
                            onClick = { selectedStatusFilter = "Configured" },
                            label = { Text("BOM Linked ($configuredDishesCount)", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = DarkGreen) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "Missing",
                            onClick = { selectedStatusFilter = "Missing" },
                            label = { Text("Missing BOM ($missingDishesCount)", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = SoftAlert) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    if (categories.size > 2) {
                        items(categories.filter { it != "All" }) { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) "All" else cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Dish BOM Recipe Cards List
        if (filteredMenuItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LayersClear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matching dishes found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(filteredMenuItems, key = { it.id }) { item ->
                val itemRecipes = allBomRecipes.filter { it.menuItemId == item.id }
                DishBomCard(
                    menuItem = item,
                    bomIngredients = itemRecipes,
                    rawMaterials = rawMaterials,
                    activeRole = activeRole,
                    onConfigureClick = { onConfigureRecipeClick(item.id) },
                    onProduceClick = { onProduceItemClick?.invoke(item.id) },
                    onDeleteRecipeClick = { itemToDeleteRecipe = item }
                )
            }
        }
    }

    // Confirmation dialog to clear/delete recipe
    if (itemToDeleteRecipe != null) {
        val target = itemToDeleteRecipe!!
        AlertDialog(
            onDismissRequest = { itemToDeleteRecipe = null },
            title = { Text("Remove BOM Recipe?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove the recipe and ingredient linkages for '${target.name}'? Raw inventory will no longer be tracked for this dish.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecipe(target.id)
                        itemToDeleteRecipe = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Recipe")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDeleteRecipe = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Individual Dish BOM Recipe Card.
 * Buttons feature Icon on top and Text at bottom for clean mobile ergonomics.
 */
@Composable
private fun DishBomCard(
    menuItem: MenuItem,
    bomIngredients: List<BomIngredient>,
    rawMaterials: List<RawMaterial>,
    activeRole: String,
    onConfigureClick: () -> Unit,
    onProduceClick: () -> Unit,
    onDeleteRecipeClick: () -> Unit
) {
    val hasRecipe = bomIngredients.isNotEmpty()
    val yieldVal = if (menuItem.recipeYield > 0) menuItem.recipeYield else 1.0

    // Compute total batch cost & portion cost
    val totalBatchCost = bomIngredients.sumOf { ing ->
        val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
        ing.requiredQuantity * (raw?.avgCostPerUnit ?: 0.0)
    }
    val costPerPortion = if (yieldVal > 0) totalBatchCost / yieldVal else 0.0
    val sellingPrice = menuItem.price
    val foodCostPct = if (sellingPrice > 0) (costPerPortion / sellingPrice) * 100.0 else 0.0
    val grossProfit = sellingPrice - costPerPortion

    // Compute maximum portions preparable with current on-hand raw materials
    val maxPreparablePortions = remember(bomIngredients, rawMaterials, yieldVal) {
        if (bomIngredients.isEmpty()) 0.0
        else {
            val possiblePortionsList = bomIngredients.mapNotNull { ing ->
                val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                if (raw == null || ing.requiredQuantity <= 0) null
                else {
                    val batchesAvailable = raw.quantity / ing.requiredQuantity
                    batchesAvailable * yieldVal
                }
            }
            if (possiblePortionsList.isNotEmpty()) possiblePortionsList.minOrNull() ?: 0.0 else 0.0
        }
    }

    // Find the bottleneck raw ingredient limiting production
    val bottleneckIngredient = remember(bomIngredients, rawMaterials, yieldVal) {
        if (bomIngredients.isEmpty()) null
        else {
            bomIngredients.minByOrNull { ing ->
                val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                if (raw == null || ing.requiredQuantity <= 0) 0.0 else raw.quantity / ing.requiredQuantity
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigureClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.2.dp,
            if (hasRecipe) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else SoftAlert.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Dish Name, Category, Price, Stock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = menuItem.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = menuItem.category.ifBlank { "General" },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Price: Rs. ${String.format(java.util.Locale.US, "%.2f", menuItem.price)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Stock: ${menuItem.availableQuantity.toInt()} portions",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (menuItem.availableQuantity > 0) DarkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            if (!hasRecipe) {
                // Unconfigured Recipe State
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { onConfigureClick() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SoftAlert,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Raw Ingredients Linked to this Dish",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftAlert
                        )
                        Text(
                            text = "Tap below to define recipe ingredients and automatic inventory deduction.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Link Recipe Button on New Line (Icon Top, Text Bottom)
                Button(
                    onClick = onConfigureClick,
                    enabled = activeRole == "Admin",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("configure_bom_button_${menuItem.id}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Link Recipe Ingredients (BOM)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Configured Recipe Economics & Ingredients
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Recipe Yield & Food Cost Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DarkGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Yield: ${yieldVal.toInt()} portion(s)/batch",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Food Cost Tag
                        val foodCostColor = when {
                            foodCostPct <= 32 -> DarkGreen
                            foodCostPct <= 45 -> StatusYellow
                            else -> SoftAlert
                        }
                        Text(
                            text = "Food Cost: ${String.format(java.util.Locale.US, "%.1f", foodCostPct)}% (Rs. ${String.format(java.util.Locale.US, "%.2f", costPerPortion)}/dish)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = foodCostColor
                        )
                    }

                    // Kitchen Prep Capacity & Bottleneck Info
                    val bottleneckRaw = rawMaterials.firstOrNull { it.id == bottleneckIngredient?.rawItemId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SoupKitchen,
                                contentDescription = null,
                                tint = if (maxPreparablePortions > 5) DarkGreen else SoftAlert,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Prep Capacity: ${maxPreparablePortions.toInt()} portions",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (maxPreparablePortions > 5) DarkGreen else SoftAlert
                            )
                        }

                        if (bottleneckRaw != null && maxPreparablePortions < 100) {
                            Text(
                                text = "Limiting: ${bottleneckRaw.name} (${bottleneckRaw.quantity} ${bottleneckRaw.unit})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Linked Raw Material Items
                    Text(
                        text = "Linked Raw Materials (${bomIngredients.size} items):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        bomIngredients.forEach { ing ->
                            val raw = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                            val unitCost = raw?.avgCostPerUnit ?: 0.0
                            val lineCost = ing.requiredQuantity * unitCost
                            val perDishQty = if (yieldVal > 0) ing.requiredQuantity / yieldVal else 0.0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Kitchen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = raw?.name ?: ing.rawItemName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Per dish: ${String.format(java.util.Locale.US, "%.3f", perDishQty)} ${ing.unit} • Stock: ${raw?.quantity ?: 0.0} ${ing.unit}",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", ing.requiredQuantity)} ${ing.unit}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Rs. ${String.format(java.util.Locale.US, "%.2f", lineCost)}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Dedicated Action Buttons on a New Line (Icon Top, Text Bottom)
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onConfigureClick,
                            enabled = activeRole == "Admin",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("configure_bom_button_${menuItem.id}"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Edit Recipe BOM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (activeRole == "Admin") {
                            OutlinedButton(
                                onClick = onDeleteRecipeClick,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftAlert),
                                border = BorderStroke(1.dp, SoftAlert.copy(alpha = 0.5f)),
                                modifier = Modifier.height(48.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear Recipe",
                                        tint = SoftAlert,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Clear",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftAlert
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
