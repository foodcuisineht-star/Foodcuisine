package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.RestaurantViewModel
import com.example.ui.bom.BomManagementScreen
import com.example.ui.bom.RecipeDesignerDialog
import com.example.ui.receipt.Thermal80mmReceiptDialog
import com.example.ui.receipt.printFixed80mmHtmlReceipt
import com.example.ui.settings.PrinterSettingsSection
import com.example.ui.viewmodel.TodayMetrics
import com.example.ui.viewmodel.MonthlyMetrics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        
        val maxDimension = 250
        val width = originalBitmap.width
        val height = originalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            val ratio = width.toFloat() / height.toFloat()
            (maxDimension to (maxDimension / ratio).toInt())
        } else {
            val ratio = height.toFloat() / width.toFloat()
            ((maxDimension / ratio).toInt() to maxDimension)
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap? {
    return try {
        if (base64Str.isBlank()) return null
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private enum class AdminTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    ORDERS("Orders", Icons.Default.ShoppingCart),
    RIDERS("Riders", Icons.Default.DirectionsBike),
    PRODUCTION("Production", Icons.Default.SoupKitchen),
    INVENTORY("Inventory", Icons.Default.Inventory),
    BOM("BOM", Icons.Default.Layers),
    REPORTS("Reports", Icons.Default.Analytics),
    SETTINGS("Settings", Icons.Default.Settings)
}

val LocalScreenMode = androidx.compose.runtime.staticCompositionLocalOf { "Mobile" }
val LocalTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1.0f }
val LocalButtonScale = androidx.compose.runtime.staticCompositionLocalOf { 1.0f }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val screenMode by viewModel.screenMode.collectAsStateWithLifecycle()
    val receiptSize by viewModel.receiptSize.collectAsStateWithLifecycle()
    val logoBase64 by viewModel.restaurantLogoBase64.collectAsStateWithLifecycle()
    val textScaleFactor by viewModel.textScaleFactor.collectAsStateWithLifecycle()
    val hideSystemBars by viewModel.hideSystemBars.collectAsStateWithLifecycle()

    val lowStockItems = remember(rawMaterials) {
        rawMaterials.filter { it.quantity <= it.lowStockThreshold }
    }

    var selectedTab by remember { mutableStateOf(AdminTab.DASHBOARD) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }

    // Dialog state variables
    var showCreateOrderDialog by remember { mutableStateOf(false) }
    var showProduceDialog by remember { mutableStateOf(false) }
    var showAddRawDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialog by remember { mutableStateOf(false) }
    var showAddMenuDialog by remember { mutableStateOf(false) }
    var showRecipeDesignerDialog by remember { mutableStateOf(false) }
    var showLowStockDialog by remember { mutableStateOf(false) }
    var selectedRecipeMenuItemId by remember { mutableStateOf<Int?>(null) }
    var editingRawMaterial by remember { mutableStateOf<RawMaterial?>(null) }

    // Toast/Snackbar notification logic
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val textScale = if (textScaleFactor == 1.0f) {
        if (screenMode == "Tablet") 1.25f else 1.0f
    } else {
        textScaleFactor
    }
    val buttonScale = if (textScaleFactor == 1.0f) {
        if (screenMode == "Tablet") 1.22f else 1.0f
    } else {
        textScaleFactor * 0.976f
    }

    CompositionLocalProvider(
        LocalScreenMode provides screenMode,
        LocalTextScale provides textScale,
        LocalButtonScale provides buttonScale
    ) {
        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
        } else if (activeUser == null) {
            ProfileSelectionScreen(
                users = users,
                logoBase64 = logoBase64,
                onSelectUser = { viewModel.selectUser(it) }
            )
        } else {
            Scaffold(
            modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = "Food Cuisine",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "ENTERPRISE MINI ERP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val lowStockCount = lowStockItems.size
                    IconButton(
                        onClick = { showLowStockDialog = true },
                        modifier = Modifier.padding(end = 8.dp).testTag("low_stock_bell_button")
                    ) {
                        Box {
                            Icon(
                                imageVector = if (lowStockCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Low Stock Alerts",
                                tint = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                            )
                            if (lowStockCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$lowStockCount",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Modern minimalist Profile avatar element matching the HTML design perfectly
                    if (users.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                                .clickable {
                                    // Log out to show the login user / role selection screen
                                    viewModel.selectUser(null)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = activeUser?.username?.take(2)?.uppercase() ?: "JD"
                                Text(
                                    text = initials,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = activeUser?.username ?: "Guest",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = activeUser?.role ?: "No Profile",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (screenMode == "Mobile") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val primaryTabs = listOf(
                        AdminTab.DASHBOARD,
                        AdminTab.ORDERS,
                        AdminTab.RIDERS,
                        AdminTab.PRODUCTION,
                        AdminTab.INVENTORY
                    )
                    primaryTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(text = tab.title, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Custom 5th tab: "More" quick access hub
                    val isMoreSelected = selectedTab == AdminTab.REPORTS || selectedTab == AdminTab.SETTINGS
                    NavigationBarItem(
                        selected = isMoreSelected,
                        onClick = { showMoreMenu = true },
                        icon = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More Options") },
                        label = { Text(text = "More", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (screenMode == "Tablet") {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val primaryTabs = listOf(
                        AdminTab.DASHBOARD,
                        AdminTab.ORDERS,
                        AdminTab.RIDERS,
                        AdminTab.PRODUCTION,
                        AdminTab.INVENTORY
                    )
                    primaryTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Custom 5th tab: "More" quick access hub
                    val isMoreSelected = selectedTab == AdminTab.REPORTS || selectedTab == AdminTab.SETTINGS
                    NavigationRailItem(
                        selected = isMoreSelected,
                        onClick = { showMoreMenu = true },
                        icon = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More Options") },
                        label = { Text(text = "More", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Nested Screen Switching with animation
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    AdminTab.DASHBOARD -> DashboardTab(
                        viewModel = viewModel,
                        activeRole = activeUser?.role ?: "Cashier",
                        onCreateOrderClick = { showCreateOrderDialog = true },
                        onProduceClick = { showProduceDialog = true },
                        onRecordPurchaseClick = { showRecordPurchaseDialog = true }
                    )
                    AdminTab.ORDERS -> OrdersTab(
                        viewModel = viewModel,
                        onCreateOrderClick = { showCreateOrderDialog = true }
                    )
                    AdminTab.RIDERS -> RidersTab(
                        viewModel = viewModel
                    )
                    AdminTab.PRODUCTION -> ProductionTab(
                        viewModel = viewModel,
                        activeRole = activeUser?.role ?: "Cashier",
                        onRunProductionClick = { showProduceDialog = true }
                    )
                    AdminTab.INVENTORY -> InventoryTab(
                        viewModel = viewModel,
                        activeRole = activeUser?.role ?: "Cashier",
                        onAddRawMaterialClick = { showAddRawDialog = true },
                        onRecordPurchaseClick = { showRecordPurchaseDialog = true },
                        onEditRawMaterialClick = { editingRawMaterial = it }
                    )
                    AdminTab.BOM -> BOMTab(
                        viewModel = viewModel,
                        activeRole = activeUser?.role ?: "Cashier",
                        onConfigureRecipeClick = { mId ->
                            selectedRecipeMenuItemId = mId
                            showRecipeDesignerDialog = true
                        },
                        onAddMenuClick = { showAddMenuDialog = true }
                    )
                    AdminTab.REPORTS -> ReportsTab(
                        viewModel = viewModel
                    )
                    AdminTab.SETTINGS -> SettingsTab(
                        viewModel = viewModel,
                        activeRole = activeUser?.role ?: "Cashier",
                        logoBase64 = logoBase64,
                        onAddMenuClick = { showAddMenuDialog = true },
                        onConfigureRecipeClick = { mId ->
                            selectedRecipeMenuItemId = mId
                            showRecipeDesignerDialog = true
                        }
                    )
                }
            }

            // --- Floating Alert/Role banners ---
            if (activeUser?.role != "Admin") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f))
                        .padding(vertical = 4.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val modeText = when (activeUser?.role) {
                            "Cashier" -> "Cashier mode: Setting configurations are read-only. Tap profile above to switch."
                            "Staff" -> "Staff mode: Configuration settings & reports are read-only. Tap profile above to switch."
                            else -> "Limited mode: Setting configurations are read-only. Tap profile above to switch."
                        }
                        Text(
                            text = modeText,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

    // --- DIALOG MODALS POPUPS ---

    if (showLowStockDialog) {
        AlertDialog(
            onDismissRequest = { showLowStockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low Stock Notifications",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "The following raw material ingredients have dropped below defined safety stock thresholds:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (lowStockItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "All raw ingredients are fully stocked above safety limits!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lowStockItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Safety threshold: ${item.lowStockThreshold} ${item.unit}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.error)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${item.quantity} ${item.unit}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                showLowStockDialog = false
                                                editingRawMaterial = item
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Stock",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lowStockItems.isNotEmpty()) {
                        Button(
                            onClick = {
                                showLowStockDialog = false
                                showRecordPurchaseDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restock Materials", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = { showLowStockDialog = false }
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    if (showMoreMenu) {
        AlertDialog(
            onDismissRequest = { showMoreMenu = false },
            title = {
                Column {
                    Text(
                        "Terminal Quick Hub",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Quick POS Operations & Management Panels",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Grid of Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Reports Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = AdminTab.REPORTS
                                    showMoreMenu = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Reports",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Settings Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = AdminTab.SETTINGS
                                    showMoreMenu = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Add Menu Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showAddMenuDialog = true
                                    showMoreMenu = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Add Menu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Run Batch Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showProduceDialog = true
                                    showMoreMenu = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SoupKitchen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Run Batch",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTab = AdminTab.RIDERS
                                showMoreMenu = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Riders Delivery Logistics",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Restock Card (Full width for visual rhythm)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRecordPurchaseDialog = true
                                showMoreMenu = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Input,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Restock & Record Material Purchase",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreMenu = false }) {
                    Text("Close Hub")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showCreateOrderDialog) {
        CreateOrderDialog(
            viewModel = viewModel,
            onDismiss = { showCreateOrderDialog = false }
        )
    }

    if (showProduceDialog) {
        ProduceBatchDialog(
            viewModel = viewModel,
            onDismiss = { showProduceDialog = false }
        )
    }

    if (showAddRawDialog) {
        AddRawMaterialDialog(
            viewModel = viewModel,
            onDismiss = { showAddRawDialog = false }
        )
    }

    if (showRecordPurchaseDialog) {
        RecordPurchaseDialog(
            viewModel = viewModel,
            onDismiss = { showRecordPurchaseDialog = false }
        )
    }

    if (showAddMenuDialog) {
        AddMenuItemDialog(
            viewModel = viewModel,
            onDismiss = { showAddMenuDialog = false }
        )
    }

    if (showRecipeDesignerDialog && selectedRecipeMenuItemId != null) {
        RecipeDesignerDialog(
            menuItemId = selectedRecipeMenuItemId!!,
            viewModel = viewModel,
            onDismiss = {
                showRecipeDesignerDialog = false
                selectedRecipeMenuItemId = null
            }
        )
    }

    if (editingRawMaterial != null) {
        EditRawMaterialDialog(
            rawMaterial = editingRawMaterial!!,
            viewModel = viewModel,
            onDismiss = { editingRawMaterial = null }
        )
    }
}
}
}

// ==========================================
// 1. DASHBOARD TAB SCREEN
// ==========================================
@Composable
private fun DashboardTab(
    viewModel: RestaurantViewModel,
    activeRole: String,
    onCreateOrderClick: () -> Unit,
    onProduceClick: () -> Unit,
    onRecordPurchaseClick: () -> Unit
) {
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val metricsToday by viewModel.metricsToday.collectAsStateWithLifecycle(TodayMetrics(0.0, 0, 0.0, emptyList()))
    val metricsMonthly by viewModel.metricsMonthly.collectAsStateWithLifecycle(MonthlyMetrics(0.0, 0, 0.0, 0.0, 0.0, emptyList(), emptyList()))

    val lowStockItems = remember(rawMaterials) {
        rawMaterials.filter { it.quantity <= it.lowStockThreshold }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Operational Overview",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                            val jobName = "Food Cuisine Operational Report"
                            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                            val lowStockHtml = if (lowStockItems.isEmpty()) {
                                "<p style='color: green; font-weight: bold;'>All raw material quantities are healthy!</p>"
                            } else {
                                """
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Item Name</th>
                                            <th>Current Qty</th>
                                            <th>Threshold</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${lowStockItems.map { item ->
                                            """
                                            <tr>
                                                <td>${item.name}</td>
                                                <td>${item.quantity} ${item.unit}</td>
                                                <td>${item.lowStockThreshold} ${item.unit}</td>
                                            </tr>
                                            """
                                        }.joinToString("")}
                                    </tbody>
                                </table>
                                """
                            }
                            val htmlContent = """
                                <html>
                                <head>
                                    <style>
                                        body { font-family: sans-serif; padding: 25px; color: #333; }
                                        h1 { text-align: center; color: #d32f2f; margin-bottom: 5px; }
                                        .center { text-align: center; }
                                        .meta { color: #666; font-size: 12px; text-align: center; margin-bottom: 25px; }
                                        .section-title { font-size: 16px; font-weight: bold; border-bottom: 2px solid #ccc; padding-bottom: 5px; margin-top: 25px; margin-bottom: 15px; }
                                        .metrics-grid { display: flex; flex-wrap: wrap; justify-content: space-between; margin-bottom: 20px; }
                                        .metric-card { flex: 0 0 48%; border: 1px solid #ddd; padding: 15px; border-radius: 8px; margin-bottom: 12px; box-sizing: border-box; }
                                        .metric-label { font-size: 11px; color: #888; text-transform: uppercase; margin-bottom: 5px; }
                                        .metric-value { font-size: 18px; font-weight: bold; color: #222; }
                                        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                                        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; font-size: 12px; }
                                        th { background-color: #f2f2f2; }
                                    </style>
                                </head>
                                <body>
                                    <h1>Food Cuisine</h1>
                                    <p class="center" style="margin: 0; font-weight: bold;">Operational & Sales Performance Report</p>
                                    <p class="meta">Report generated on: $dateStr</p>
                                    
                                    <div class="section-title">Key Performance Indicators (KPIs)</div>
                                    <div class="metrics-grid">
                                        <div class="metric-card">
                                            <div class="metric-label">Today's Sales Revenue</div>
                                            <div class="metric-value">Rs. ${String.format("%.2f", metricsToday.totalRevenue)}</div>
                                        </div>
                                        <div class="metric-card">
                                            <div class="metric-label">Today's Orders</div>
                                            <div class="metric-value">${metricsToday.ordersCount} orders</div>
                                        </div>
                                        <div class="metric-card">
                                            <div class="metric-label">Monthly Sales Revenue</div>
                                            <div class="metric-value">Rs. ${String.format("%.2f", metricsMonthly.totalRevenue)}</div>
                                        </div>
                                        <div class="metric-card">
                                            <div class="metric-label">Monthly Net Profit</div>
                                            <div class="metric-value">Rs. ${String.format("%.2f", metricsMonthly.netProfit)}</div>
                                        </div>
                                    </div>
                                    
                                    <div class="section-title">Inventory Health Alerts</div>
                                    $lowStockHtml
                                    
                                    <p class="center" style="margin-top: 40px; font-size: 10px; color: #999;">End of Food Cuisine Performance Report</p>
                                </body>
                                </html>
                            """.trimIndent()

                            val webView = android.webkit.WebView(context)
                            webView.webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                                    printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                                }
                            }
                            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("print_report_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = "Print Report", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Metrics Grid (Today's metrics and Monthly P&L)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard(
                        title = "Today's Sales",
                        value = "Rs. ${String.format("%.2f", metricsToday.totalRevenue)}",
                        icon = Icons.Default.TrendingUp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardCard(
                        title = "Today's Orders",
                        value = "${metricsToday.ordersCount}",
                        icon = Icons.Default.ShoppingCart,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardCard(
                        title = "Monthly Sales",
                        value = "Rs. ${String.format("%.2f", metricsMonthly.totalRevenue)}",
                        icon = Icons.Default.AccountBalanceWallet,
                        containerColor = SlateDarkSurface,
                        iconColor = AccentHoney,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Monthly Net Profit calculation
                    val profitVal = metricsMonthly.netProfit
                    val profitColor = if (profitVal >= 0) DarkGreen else SoftAlert
                    DashboardCard(
                        title = "Monthly Net Profit",
                        value = "${if (profitVal < 0) "-" else ""}Rs. ${String.format("%.2f", Math.abs(profitVal))}",
                        icon = if (profitVal >= 0) Icons.Default.Payments else Icons.Default.TrendingDown,
                        containerColor = if (profitVal >= 0) DarkGreen.copy(alpha = 0.15f) else SoftAlert.copy(alpha = 0.15f),
                        iconColor = profitColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Fast Quick Action POS panel (Role Locked)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Dispatch POS Tools",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCreateOrderClick,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(54.dp)
                                .testTag("quick_new_order_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("New Order", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onProduceClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("quick_produce_button"),
                            enabled = activeRole == "Admin",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Kitchen Run", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onRecordPurchaseClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("quick_buy_button"),
                            enabled = activeRole == "Admin",
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Buy Stock", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Low stock threshold alerts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (lowStockItems.isNotEmpty()) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (lowStockItems.isNotEmpty()) Color(0xFFFED7AA) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (lowStockItems.isNotEmpty()) Color(0xFFFFE4E6) else Color(0xFFD1FAE5)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (lowStockItems.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (lowStockItems.isNotEmpty()) Color(0xFFEA580C) else Color(0xFF059669),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Larder & Raw Material Stock Alerts",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                color = if (lowStockItems.isNotEmpty()) Color(0xFFC2410C) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (lowStockItems.isNotEmpty()) {
                            Badge(containerColor = Color(0xFFEA580C)) {
                                Text(
                                    "${lowStockItems.size} Low",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (lowStockItems.isEmpty()) {
                        Text(
                            text = "All raw materials are safely stocked above minimum alert thresholds.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        lowStockItems.forEach { raw ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = raw.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${String.format("%.2f", raw.quantity)} ${raw.unit}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SoftAlert
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "(Min: ${raw.lowStockThreshold} ${raw.unit})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ready menu stock display
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Prepared Ready Food Stock (Menus)".uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (menuItems.isEmpty()) {
                        Text(
                            text = "No menu items registered in system settings yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        menuItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (!item.hasBom) {
                                        Text("No recipe bound (Indirect)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    } else {
                                        Text("Recipe batch: ${item.recipeYield.toInt()} portions", fontSize = 10.sp, color = DarkGreen)
                                    }
                                }
                                val qtyColor = if (item.availableQuantity <= 3) SoftAlert else if (item.availableQuantity <= 10) StatusYellow else DarkGreen
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(qtyColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${item.availableQuantity.toInt()} units available",
                                        color = qtyColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun DashboardCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (title.contains("Profit", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ==========================================
// 2. ORDERS / SALES TAB SCREEN
// ==========================================
@Composable
private fun OrdersTab(
    viewModel: RestaurantViewModel,
    onCreateOrderClick: () -> Unit
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val receiptSize by viewModel.receiptSize.collectAsStateWithLifecycle()
    val showLogoOnReceipt by viewModel.showLogoOnReceipt.collectAsStateWithLifecycle()
    val showTaxOnReceipt by viewModel.showTaxOnReceipt.collectAsStateWithLifecycle()
    val receiptFooterNote by viewModel.receiptFooterNote.collectAsStateWithLifecycle()
    val restaurantName by viewModel.restaurantName.collectAsStateWithLifecycle()
    val restaurantSlogan by viewModel.restaurantSlogan.collectAsStateWithLifecycle()
    val restaurantPhone by viewModel.restaurantPhone.collectAsStateWithLifecycle()
    val restaurantAddress by viewModel.restaurantAddress.collectAsStateWithLifecycle()
    val logoBase64 by viewModel.restaurantLogoBase64.collectAsStateWithLifecycle()

    var viewing80mmOrder by remember { mutableStateOf<Order?>(null) }
    var viewing80mmOrderItems by remember { mutableStateOf<List<OrderItem>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = "Customer Orders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${orders.size} total entries recorded · 80mm Fixed Print Format",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCreateOrderClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sales_new_order_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("New Order", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (orders.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No orders placed yet.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("Tap \"New Order\" to construct a customer ticket.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders) { order ->
                    var isExpanded by remember { mutableStateOf(false) }
                    val orderItemsFlow = remember(order.id) { viewModel.getOrderItemsForOrderFlow(order.id) }
                    val orderItems by orderItemsFlow.collectAsStateWithLifecycle(emptyList())

                    var isEditingRider by remember { mutableStateOf(false) }
                    var riderNameInput by remember { mutableStateOf(order.riderName) }
                    var riderPhoneInput by remember { mutableStateOf(order.riderPhone) }
                    var riderBikeInput by remember { mutableStateOf(order.riderBikeNumber) }
                    var riderChargesInput by remember { mutableStateOf(if (order.riderCharges > 0) order.riderCharges.toString() else "") }

                    LaunchedEffect(order) {
                        riderNameInput = order.riderName
                        riderPhoneInput = order.riderPhone
                        riderBikeInput = order.riderBikeNumber
                        riderChargesInput = if (order.riderCharges > 0) order.riderCharges.toString() else ""
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .testTag("order_card_${order.orderNumber}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Status badge on LEFT TOP SIDE, Total Amount on RIGHT TOP
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Order Status Badge on LEFT TOP side
                                OrderStatusBadge(status = order.status)

                                Text(
                                    text = "Rs. ${String.format("%.2f", order.totalAmount)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // New Line below Status: Order ID, Type Badge, and Item Count
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = order.orderNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(30.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = order.type,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "${order.totalItemsQuantity.toInt()} items",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date(order.orderDate)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            if (order.customerName.isNotEmpty() || order.customerPhone.isNotEmpty() || order.paymentMethod.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (order.customerName.isNotEmpty()) {
                                                if (order.customerPhone.isNotEmpty()) "${order.customerName} (${order.customerPhone})" else order.customerName
                                            } else if (order.customerPhone.isNotEmpty()) {
                                                order.customerPhone
                                            } else {
                                                "Walk-In Customer"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }

                                    // Payment Method and Status Badges
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Payment Method Badge
                                        val displayMethod = when(order.paymentMethod) {
                                            "Credit" -> "Credit"
                                            "Scheduled" -> "Scheduled"
                                            else -> order.paymentMethod
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = displayMethod,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        // If Credit or Scheduled, show payment status badge
                                        if (order.paymentMethod == "Credit" || order.paymentMethod == "Scheduled") {
                                            val isPaid = order.paymentStatus == "Paid"
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isPaid) DarkGreen.copy(alpha = 0.15f) else SoftAlert.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isPaid) "PAID" else "UNPAID",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isPaid) DarkGreen else SoftAlert
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Expanded detail view showing purchased items and state actions!
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .fillMaxWidth()
                                ) {
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Extended customer profile details & schedule credits panel
                                    if (order.customerName.isNotEmpty() || order.customerPhone.isNotEmpty() || order.creditDueDate != null || order.customerAddress.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            if (order.customerName.isNotEmpty() || order.customerPhone.isNotEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Customer: ${order.customerName} ${if (order.customerPhone.isNotEmpty()) "· ${order.customerPhone}" else ""}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            if (order.customerAddress.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Address: ${order.customerAddress}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (order.paymentMethod.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    val methodLabel = when (order.paymentMethod) {
                                                        "Credit" -> "Customer Credit / On Account"
                                                        "Scheduled" -> "Scheduled Payment Plan"
                                                        else -> order.paymentMethod
                                                    }
                                                    Text(
                                                        text = "Payment Method: $methodLabel",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (order.creditDueDate != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = SoftAlert)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    val formattedDueDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(order.creditDueDate))
                                                    val daysRemaining = ((order.creditDueDate - System.currentTimeMillis()) / (1000L * 3600L * 24L)).toInt()
                                                    val statusText = if (order.paymentStatus == "Paid") {
                                                        "Settled (Paid)"
                                                    } else if (daysRemaining < 0) {
                                                        "Overdue by ${-daysRemaining} days (Unpaid)"
                                                    } else {
                                                        "Due in $daysRemaining days (on $formattedDueDate)"
                                                    }
                                                    Text(
                                                        text = "Credit Term: $statusText",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (order.paymentStatus == "Paid") DarkGreen else if (daysRemaining < 0) SoftAlert else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Rider Delivery Assignment Card
                                    if (order.type != "Dine-In") {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Rider Delivery Logistics", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    
                                                    if (!isEditingRider) {
                                                        IconButton(
                                                            onClick = { isEditingRider = true },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Rider", modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                if (isEditingRider) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OutlinedTextField(
                                                                value = riderNameInput,
                                                                onValueChange = { riderNameInput = it },
                                                                label = { Text("Rider Name", fontSize = 10.sp) },
                                                                singleLine = true,
                                                                modifier = Modifier.weight(1f).height(48.dp),
                                                                textStyle = TextStyle(fontSize = 12.sp)
                                                            )
                                                            OutlinedTextField(
                                                                value = riderPhoneInput,
                                                                onValueChange = { riderPhoneInput = it },
                                                                label = { Text("Rider Phone", fontSize = 10.sp) },
                                                                singleLine = true,
                                                                modifier = Modifier.weight(1f).height(48.dp),
                                                                textStyle = TextStyle(fontSize = 12.sp),
                                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OutlinedTextField(
                                                                value = riderBikeInput,
                                                                onValueChange = { riderBikeInput = it },
                                                                label = { Text("Bike Number", fontSize = 10.sp) },
                                                                singleLine = true,
                                                                modifier = Modifier.weight(1.2f).height(48.dp),
                                                                textStyle = TextStyle(fontSize = 12.sp)
                                                            )
                                                            OutlinedTextField(
                                                                value = riderChargesInput,
                                                                onValueChange = { riderChargesInput = it },
                                                                label = { Text("Charges (Rs.)", fontSize = 10.sp) },
                                                                singleLine = true,
                                                                modifier = Modifier.weight(1f).height(48.dp),
                                                                textStyle = TextStyle(fontSize = 12.sp),
                                                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            TextButton(
                                                                onClick = { 
                                                                    isEditingRider = false
                                                                    riderNameInput = order.riderName
                                                                    riderPhoneInput = order.riderPhone
                                                                    riderBikeInput = order.riderBikeNumber
                                                                    riderChargesInput = if (order.riderCharges > 0) order.riderCharges.toString() else ""
                                                                }
                                                            ) {
                                                                Text("Cancel", fontSize = 11.sp)
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Button(
                                                                onClick = {
                                                                    viewModel.updateRiderDetails(
                                                                        order.id,
                                                                        riderNameInput,
                                                                        riderPhoneInput,
                                                                        riderBikeInput,
                                                                        riderChargesInput.toDoubleOrNull() ?: 0.0
                                                                    )
                                                                    isEditingRider = false
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                                modifier = Modifier.height(32.dp),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    if (order.riderName.isEmpty() && order.riderPhone.isEmpty() && order.riderBikeNumber.isEmpty() && order.riderCharges <= 0.0) {
                                                        Text(
                                                            text = "No rider assigned yet. Click the edit icon to dispatch a rider.",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    } else {
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            if (order.riderName.isNotEmpty() || order.riderPhone.isNotEmpty()) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = "Rider Name: ${order.riderName} ${if (order.riderPhone.isNotEmpty()) "· ${order.riderPhone}" else ""}",
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            if (order.riderBikeNumber.isNotEmpty()) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(imageVector = Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = "Bike Number: ${order.riderBikeNumber}",
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = "Delivery Charges: Rs. ${String.format("%.2f", order.riderCharges)}",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Mark payment as settled button if credit unpaid
                                    if ((order.paymentMethod == "Credit" || order.paymentMethod == "Scheduled") && order.paymentStatus != "Paid") {
                                        Button(
                                            onClick = { viewModel.updateOrderPaymentStatus(order.id, "Paid") },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("settle_payment_button_${order.id}")
                                        ) {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Mark Payment as Settled / Collected", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    Text(
                                        text = "Ticket details:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    orderItems.forEach { ticketItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${ticketItem.quantity.toInt()}x ${ticketItem.menuItemName}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Rs. ${String.format("%.2f", ticketItem.totalAmount)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Print & Share Section
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val receiptText = remember(order, orderItems) {
                                        val builder = java.lang.StringBuilder()
                                        builder.append("=======================\n")
                                        builder.append("    Food Cuisine POS   \n")
                                        builder.append("=======================\n")
                                        builder.append("Order No : ${order.orderNumber}\n")
                                         builder.append("Customer : ${if (order.customerName.isNotBlank()) order.customerName else "Walk-in"}\n")
                                         builder.append("Phone    : ${if (order.customerPhone.isNotBlank()) order.customerPhone else "N/A"}\n")
                                        builder.append("Type     : ${order.type}\n")
                                        builder.append("Status   : ${order.status}\n")
                                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(order.orderDate))
                                        builder.append("Date     : $dateStr\n")
                                        builder.append("-----------------------\n")
                                        orderItems.forEach { item ->
                                            val itemTotal = String.format("%.2f", item.totalAmount)
                                            builder.append("${item.quantity.toInt()}x ${item.menuItemName}\n")
                                            builder.append("   @ Rs. ${String.format("%.2f", item.unitPrice)} -> Rs. $itemTotal\n")
                                        }
                                        builder.append("-----------------------\n")
                                        builder.append("Subtotal : Rs. ${String.format("%.2f", order.subtotal)}\n")
                                        builder.append("Tax (VAT): Rs. ${String.format("%.2f", order.taxAmount)}\n")
                                        builder.append("Total    : Rs. ${String.format("%.2f", order.totalAmount)}\n")
                                        builder.append("=======================\n")
                                        builder.append(" Thank you for your order! \n")
                                        builder.append("=======================\n")
                                        builder.toString()
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 1. View 80mm Receipt Bill (Fixed Page Layout Modal)
                                        OutlinedButton(
                                            onClick = {
                                                viewing80mmOrder = order
                                                viewing80mmOrderItems = orderItems
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp).testTag("view_80mm_order_${order.orderNumber}"),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                        ) {
                                            Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = "View 80mm Bill", modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("View 80mm", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        // 2. Direct Print 80mm Bill
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
                                                    showLogo = showLogoOnReceipt,
                                                    showTax = showTaxOnReceipt,
                                                    footerNote = receiptFooterNote
                                                )
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp).testTag("print_80mm_order_${order.orderNumber}"),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Default.Print, contentDescription = "Print 80mm", modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Print 80mm", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        // 3. Share WhatsApp Bill
                                        Button(
                                            onClick = {
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, receiptText)
                                                    type = "text/plain"
                                                    `package` = "com.whatsapp"
                                                }
                                                try {
                                                    context.startActivity(sendIntent)
                                                } catch (e: Exception) {
                                                    val shareIntent = android.content.Intent.createChooser(
                                                        android.content.Intent().apply {
                                                            action = android.content.Intent.ACTION_SEND
                                                            putExtra(android.content.Intent.EXTRA_TEXT, receiptText)
                                                            type = "text/plain"
                                                        },
                                                        "Share Receipt"
                                                    )
                                                    context.startActivity(shareIntent)
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF25D366),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share on WhatsApp", modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons for statuses
                                    if (order.status != "Completed" && order.status != "Cancelled") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Change Status: ",
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )

                                            // Status Transitions
                                            if (order.status == "Pending") {
                                                Button(
                                                    onClick = { viewModel.updateOrderStatus(order.id, "Preparing") },
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                                ) {
                                                    Text("Prep", fontSize = 11.sp)
                                                }
                                            }

                                            if (order.status == "Preparing") {
                                                Button(
                                                    onClick = { viewModel.updateOrderStatus(order.id, "Ready") },
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                                ) {
                                                    Text("Ready", fontSize = 11.sp)
                                                }
                                            }

                                            if (order.status == "Ready") {
                                                Button(
                                                    onClick = { viewModel.updateOrderStatus(order.id, "Completed") },
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                                                ) {
                                                    Text("Serve", fontSize = 11.sp)
                                                }
                                            }

                                            Button(
                                                onClick = { viewModel.updateOrderStatus(order.id, "Cancelled") },
                                                shape = RoundedCornerShape(4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = SoftAlert),
                                                modifier = Modifier.height(32.dp).testTag("cancel_order_${order.orderNumber}"),
                                                contentPadding = PaddingValues(horizontal = 10.dp)
                                            ) {
                                                Text("Cancel", fontSize = 11.sp)
                                            }
                                        }
                                    } else if (order.status == "Cancelled") {
                                        Text(
                                            text = "Order has been cancelled. Ready stocks restored.",
                                            fontSize = 11.sp,
                                            color = SoftAlert,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = "Order completed successfully.",
                                            fontSize = 11.sp,
                                            color = DarkGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Render 80mm Thermal Receipt Dialog if active
        if (viewing80mmOrder != null) {
            Thermal80mmReceiptDialog(
                order = viewing80mmOrder!!,
                orderItems = viewing80mmOrderItems,
                restaurantName = restaurantName,
                restaurantSlogan = restaurantSlogan,
                restaurantPhone = restaurantPhone,
                restaurantAddress = restaurantAddress,
                logoBase64 = logoBase64,
                receiptSize = receiptSize,
                showLogo = showLogoOnReceipt,
                showTax = showTaxOnReceipt,
                footerNote = receiptFooterNote,
                onDismiss = {
                    viewing80mmOrder = null
                    viewing80mmOrderItems = emptyList()
                }
            )
        }
    }
}

@Composable
private fun OrderStatusBadge(status: String) {
    val bg = when (status) {
        "Pending" -> Color.Gray.copy(alpha = 0.15f)
        "Preparing" -> StatusYellow.copy(alpha = 0.15f)
        "Ready" -> StatusBlue.copy(alpha = 0.15f)
        "Completed" -> DarkGreen.copy(alpha = 0.15f)
        "Cancelled" -> SoftAlert.copy(alpha = 0.15f)
        else -> Color.Gray.copy(alpha = 0.15f)
    }
    val fg = when (status) {
        "Pending" -> Color.Gray
        "Preparing" -> StatusYellow
        "Ready" -> StatusBlue
        "Completed" -> DarkGreen
        "Cancelled" -> SoftAlert
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ==========================================
// 3. PRODUCTION MODULE TAB SCREEN
// ==========================================
@Composable
private fun ProductionTab(
    viewModel: RestaurantViewModel,
    activeRole: String,
    onRunProductionClick: () -> Unit
) {
    val batches by viewModel.productionBatches.collectAsStateWithLifecycle()
    var batchToEdit by remember { mutableStateOf<ProductionBatch?>(null) }
    var batchToDelete by remember { mutableStateOf<ProductionBatch?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = "BOM Production Runs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Convert raw materials into ready-to-sell menu stock.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Button(
                onClick = onRunProductionClick,
                enabled = activeRole == "Admin",
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("production_add_batch_button"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Run Batch", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (batches.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No kitchen production batches logged yet.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("Select 'Run Batch' above to build hamburgers or items via BOM.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(batches) { batch ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = batch.menuItemName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date(batch.productionDate)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(30.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "+${batch.producedQuantity.toInt()} units",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PriceCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "BOM Cost: Rs. ${String.format("%.2f", batch.totalCost)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            // Dedicated Action Buttons on a New Line (Icon Top, Text Bottom)
                            if (activeRole == "Admin") {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { batchToEdit = batch },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
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
                                                text = "Edit Batch",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { batchToDelete = batch },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = SoftAlert
                                        ),
                                        border = BorderStroke(1.dp, SoftAlert.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = SoftAlert,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Delete",
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
    }

    // Edit Batch Dialog
    batchToEdit?.let { currentBatch ->
        var editQtyText by remember { mutableStateOf(currentBatch.producedQuantity.toInt().toString()) }
        var adjustInventory by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { batchToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Production Batch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Dish: ${currentBatch.menuItemName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Logged on: ${SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date(currentBatch.productionDate))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editQtyText,
                        onValueChange = { editQtyText = it },
                        label = { Text("Produced Quantity (Portions)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { adjustInventory = !adjustInventory }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = adjustInventory,
                            onCheckedChange = { adjustInventory = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Auto-adjust raw inventory & ready dish stock based on quantity difference",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedQty = editQtyText.toDoubleOrNull() ?: 0.0
                        if (parsedQty > 0) {
                            val updated = currentBatch.copy(producedQuantity = parsedQty)
                            viewModel.updateProductionBatch(updated, currentBatch, adjustInventory)
                            batchToEdit = null
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Save Changes", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { batchToEdit = null },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Delete Batch Dialog
    batchToDelete?.let { currentBatch ->
        var revertStock by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SoftAlert,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Production Batch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to delete this batch of ${currentBatch.producedQuantity.toInt()} portions for '${currentBatch.menuItemName}'?",
                        fontSize = 13.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { revertStock = !revertStock }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = revertStock,
                            onCheckedChange = { revertStock = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Restore raw materials to warehouse inventory and deduct ready dish stock",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProductionBatch(currentBatch, revertStock)
                        batchToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftAlert),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Delete Batch", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { batchToDelete = null },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

// ==========================================
// 3.5. BILL OF MATERIALS (BOM) MANAGEMENT TAB SCREEN
// ==========================================
@Composable
private fun BOMTab(
    viewModel: RestaurantViewModel,
    activeRole: String,
    onConfigureRecipeClick: (Int) -> Unit,
    onAddMenuClick: () -> Unit
) {
    BomManagementScreen(
        viewModel = viewModel,
        activeRole = activeRole,
        onConfigureRecipeClick = onConfigureRecipeClick,
        onAddMenuClick = onAddMenuClick
    )
}

// ==========================================
// 4. INVENTORY / PURCHASING TAB SCREEN
// ==========================================
@Composable
private fun InventoryTab(
    viewModel: RestaurantViewModel,
    activeRole: String,
    onAddRawMaterialClick: () -> Unit,
    onRecordPurchaseClick: () -> Unit,
    onEditRawMaterialClick: (RawMaterial) -> Unit
) {
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()

    var showPurchasesOnly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = "Raw Materials & Larder",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track ingredient balances and resting cost averages.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (activeRole == "Admin") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAddRawMaterialClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .testTag("inventory_create_item_icon"),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add material")
                    }

                    Button(
                        onClick = onRecordPurchaseClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(52.dp).testTag("inventory_restock_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Input, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Restock Materials", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab switcher inside Inventory (Live Stock vs Purchase Log History)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (!showPurchasesOnly) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { showPurchasesOnly = false }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Current Stock Levels",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!showPurchasesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showPurchasesOnly) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { showPurchasesOnly = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Purchase RESTOCK Log (${purchases.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showPurchasesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!showPurchasesOnly) {
            // Larder Levels Lists
            if (rawMaterials.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No inventory items found. Add ingredients using '+' icon.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rawMaterials) { raw ->
                        val isLow = raw.quantity <= raw.lowStockThreshold
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditRawMaterialClick(raw) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                if (isLow) SoftAlert.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = raw.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (isLow) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SoftAlert.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("LOW STOCK", color = SoftAlert, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Average Valuation: Rs. ${String.format("%.4f", raw.avgCostPerUnit)} per ${raw.unit}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text(
                                            text = "${String.format("%.2f", raw.quantity)} ${raw.unit}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = if (isLow) SoftAlert else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Min: ${raw.lowStockThreshold} ${raw.unit}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }

                                    // Edit Stock Button
                                    IconButton(
                                        onClick = { onEditRawMaterialClick(raw) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("inventory_edit_item_${raw.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Stock & Details",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    
                                    // Let Admin delete if uncooked
                                    if (activeRole == "Admin") {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteRawMaterial(raw) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = SoftAlert.copy(alpha = 0.7f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Purchase Log History Lists
            if (purchases.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No billing purchase records logged yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(purchases) { buy ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = buy.rawItemName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()).format(Date(buy.purchaseDate)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${String.format("%.2f", buy.quantity)} purchased",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = DarkGreen
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Spent: Rs. ${String.format("%.2f", buy.cost)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
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

// ==========================================
// 5. REPORTS / ANALYTICS TAB SCREEN
// ==========================================
@Composable
private fun ReportsTab(
    viewModel: RestaurantViewModel
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val metricsToday by viewModel.metricsToday.collectAsStateWithLifecycle(TodayMetrics(0.0, 0, 0.0, emptyList()))
    val metricsMonthly by viewModel.metricsMonthly.collectAsStateWithLifecycle(MonthlyMetrics(0.0, 0, 0.0, 0.0, 0.0, emptyList(), emptyList()))

    var selectedReportView by remember { mutableStateOf("Daily") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Business Intelligence Hub",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Review live financial, cost of manufacturing, and sales performance.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab switcher (Daily vs Custom vs Monthly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedReportView == "Daily") MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { selectedReportView = "Daily" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Today Feed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedReportView == "Daily") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedReportView == "Custom") MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { selectedReportView = "Custom" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Sales Report (Date)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedReportView == "Custom") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedReportView == "Monthly") MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { selectedReportView = "Monthly" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Monthly P&L",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedReportView == "Monthly") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedReportView == "Daily") {
            // Daily Report Panel
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Today Stats Row Summary
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("TODAY SALES TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Rs. ${String.format("%.2f", metricsToday.totalRevenue)}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("COMPLETED TICKET COUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${metricsToday.ordersList.filter { it.status == "Completed" }.size} Safe Served", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Daily sold item analysis
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Daily Product Distribution (Sold Items)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Derive dynamic items sold from today's orders
                            val completedOrdersToday = metricsToday.ordersList.filter { it.status != "Cancelled" }
                            var hasSoldAny = false

                            if (completedOrdersToday.isEmpty()) {
                                Text("No order sales logged today yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            } else {
                                // Gather item summaries of order items sold
                                // Since we load order items asynchronously for list items,
                                // we can compute the summary from all orders.
                                // We can write a quick summary helper. To guarantee synchronous report layout:
                                // Since database works reactively, we can query orders, but wait! We can inspect the orderItem quantities directly, 
                                // or display a list of completed orders with their totals.
                                // Let's list the orders with a clean table showing their items!
                                hasSoldAny = true
                                completedOrdersToday.forEach { ord ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Order ${ord.orderNumber} (${ord.type})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Rs. ${String.format("%.2f", ord.totalAmount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Cumulative feed of active tickets
                item {
                    Text("Today's Ticket Log", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }

                if (metricsToday.ordersList.isEmpty()) {
                    item {
                        Text("No orders placed yet today.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    items(metricsToday.ordersList) { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.orderNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OrderStatusBadge(status = order.status)
                                }
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(order.orderDate)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = "Rs. ${String.format("%.2f", order.totalAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else if (selectedReportView == "Custom") {
            // State for custom range selection
            var startDateLong by remember { mutableStateOf(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) } // 7 days ago
            var endDateLong by remember { mutableStateOf(System.currentTimeMillis()) } // now

            val context = androidx.compose.ui.platform.LocalContext.current

            // Helper to format date
            val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

            // Filtered list of orders in selection range
            val filteredOrders = remember(orders, startDateLong, endDateLong) {
                orders.filter { it.orderDate in startDateLong..endDateLong }
            }

            // Calculation metrics
            val completedOrders = remember(filteredOrders) {
                filteredOrders.filter { it.status == "Completed" }
            }
            val totalSales = remember(completedOrders) {
                completedOrders.sumOf { it.totalAmount }
            }
            val averageValue = remember(completedOrders, totalSales) {
                if (completedOrders.isNotEmpty()) totalSales / completedOrders.size else 0.0
            }

            // Payment modes distribution
            val paymentBreakdown = remember(completedOrders) {
                val map = mutableMapOf(
                    "Cash" to 0.0,
                    "Card" to 0.0,
                    "UPI" to 0.0,
                    "Credit" to 0.0,
                    "Scheduled" to 0.0
                )
                completedOrders.forEach { ord ->
                    val m = ord.paymentMethod ?: "Cash"
                    map[m] = (map[m] ?: 0.0) + ord.totalAmount
                }
                map
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Range selector card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Select Sales Date Range",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Start date trigger
                                Button(
                                    onClick = {
                                        val calendar = Calendar.getInstance().apply { timeInMillis = startDateLong }
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val c = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                startDateLong = c.timeInMillis
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("custom_report_start_date_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("From: ${dateFormatter.format(Date(startDateLong))}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // End date trigger
                                Button(
                                    onClick = {
                                        val calendar = Calendar.getInstance().apply { timeInMillis = endDateLong }
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val c = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                    set(Calendar.HOUR_OF_DAY, 23)
                                                    set(Calendar.MINUTE, 59)
                                                    set(Calendar.SECOND, 59)
                                                    set(Calendar.MILLISECOND, 999)
                                                }
                                                endDateLong = c.timeInMillis
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("custom_report_end_date_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("To: ${dateFormatter.format(Date(endDateLong))}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Print PDF Report Button
                            Button(
                                onClick = {
                                    try {
                                        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                                        val jobName = "Food Cuisine Custom Sales Report"
                                        
                                        // Generates HTML
                                        val htmlContent = generateCustomSalesReportHtml(
                                            startDateLong,
                                            endDateLong,
                                            filteredOrders,
                                            totalSales,
                                            completedOrders.size,
                                            averageValue,
                                            paymentBreakdown
                                        )

                                        val webView = android.webkit.WebView(context)
                                        webView.webViewClient = object : android.webkit.WebViewClient() {
                                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                                                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                                            }
                                        }
                                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("save_pdf_sales_report_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Save PDF Report", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save PDF / Print Custom Sales Report", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Analytics Overview Row Summary
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("RANGE TOTAL REVENUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                Text("Rs. ${String.format("%.2f", totalSales)}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("COMPLETED TICKET COUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                Text("${completedOrders.size} Tickets", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                // Average and breakdown
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Payment Methods Breakdown",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            val paymentList = paymentBreakdown.toList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                paymentList.forEach { (method, amount) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(method, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("Rs. ${String.format("%.2f", amount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Ticket log
                item {
                    Text("Matching Transactions Log (${filteredOrders.size} entries)", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }

                if (filteredOrders.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No orders recorded in this date range.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    items(filteredOrders) { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.orderNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OrderStatusBadge(status = order.status)
                                }
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(order.orderDate)) + 
                                           " • " + (if(order.customerName.isNotBlank()) order.customerName else "Walk-in"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Rs. ${String.format("%.2f", order.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Mode: ${order.paymentMethod ?: "Cash"}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Monthly Profit & Loss Statement (Pure Business Formula Output)
            val rev = metricsMonthly.totalRevenue
            val cop = metricsMonthly.costOfProduction
            val pur = metricsMonthly.totalExpensesPurchases
            val net = metricsMonthly.netProfit

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateDarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "MONTHLY NET PROFIT / LOSS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentHoney
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${if (net < 0) "-" else ""}Rs. ${String.format("%.2f", Math.abs(net))}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (net >= 0) DarkGreen else SoftAlert
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Formula: Sales Revenue (Rs. ${String.format("%.2f", rev)}) - Manufacturing BOM Cost (Rs. ${String.format("%.2f", cop)})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Profit components breakdown table
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Financial Components Breakdown",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Component 1: Sales Revenue
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gross Sales Revenue", fontSize = 13.sp)
                                }
                                Text(
                                    "+Rs. ${String.format("%.2f", rev)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            // Component 2: Raw Ingredients produced (COGS)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SoupKitchen, contentDescription = null, tint = AccentHoney, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Cost of Production (BOM)", fontSize = 13.sp)
                                        Text("${metricsMonthly.batchesList.size} batch runs executed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                                Text(
                                    "-Rs. ${String.format("%.2f", cop)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentHoney
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            // Component 3: Materials Acquisitions (Purchases cash spent)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = SoftAlert, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Inventory Restock Spending", fontSize = 13.sp)
                                        Text("Cash paid out to vendors", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                                Text(
                                    "-Rs. ${String.format("%.2f", pur)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftAlert
                                )
                            }
                        }
                    }
                }

                // Business advice component
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "📊 Food Margin Efficiency Guide",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val margin = if (rev > 0) ((rev - cop) / rev) * 100.0 else 0.0
                            Text(
                                text = "Your manufacturing materials gross margin is ${String.format("%.1f", margin)}%. Standard fine-dining averages around 65-70%. Try adjusting sell prices or minimizing recipe waste ratios inside Settings.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. SETTINGS & MENU MANAGEMENT TAB SCREEN
// ==========================================
@Composable
private fun SettingsTab(
    viewModel: RestaurantViewModel,
    activeRole: String,
    logoBase64: String,
    onAddMenuClick: () -> Unit,
    onConfigureRecipeClick: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val textScaleFactor by viewModel.textScaleFactor.collectAsStateWithLifecycle()
    val hideSystemBars by viewModel.hideSystemBars.collectAsStateWithLifecycle()
    var settingsSubTab by remember { mutableStateOf("Menu") } // "Menu", "Users", or "Data"

    androidx.compose.runtime.LaunchedEffect(activeRole) {
        if (activeRole != "Admin") {
            settingsSubTab = "Menu"
        }
    }

    var editingUser by remember { mutableStateOf<User?>(null) }

    if (editingUser != null) {
        val user = editingUser!!
        var editUsername by remember(user) { mutableStateOf(user.username) }
        var editPassword by remember(user) { mutableStateOf(user.password) }
        var editRole by remember(user) { mutableStateOf(user.role) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Staff Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("User Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_staff_username_input")
                    )
                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text("Password / Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_staff_password_input")
                    )
                    Column {
                        Text(
                            text = "Access Role / Level:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Admin", "Cashier", "Staff").forEach { role ->
                                val isSelected = editRole == role
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent 
                                                    else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { editRole = role }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = role,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editUsername.isNotBlank() && editPassword.isNotBlank()) {
                            viewModel.editUser(user, editUsername.trim(), editRole, editPassword.trim())
                            editingUser = null
                        }
                    },
                    modifier = Modifier.testTag("submit_edit_staff")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = when (settingsSubTab) {
                        "Users" -> "User & Staff Settings"
                        "Printer" -> "Thermal Printer & Bill Settings"
                        "Cloud" -> "Centralized Database Settings"
                        "Data" -> "Database Administration & Resets"
                        "Device" -> "Display Preferences"
                        "Logo" -> "Restaurant Branding & Logo"
                        "Profile" -> "Restaurant & Company Profile"
                        else -> "Larder & Menu Settings"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (settingsSubTab) {
                        "Users" -> "Manage staff member profiles, system passwords, and role access."
                        "Printer" -> "Configure 80mm fixed receipt bill layout, paper feeds, cutter, and thermal slip settings."
                        "Cloud" -> "Connect with cloud database endpoints or remain completely local offline."
                        "Data" -> "Wipe order transactions or perform a complete factory reset to default demo data."
                        "Device" -> "Configure adaptive screen mode layout and terminal color themes."
                        "Logo" -> "Upload brand assets or select standard system-matching layout presets."
                        "Profile" -> "Edit physical address, name, contact info, and receipt header details."
                        else -> "Regulate menu pricing, taxes, and Bill of Materials recipes."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (settingsSubTab == "Menu" && activeRole == "Admin") {
                Button(
                    onClick = onAddMenuClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("settings_create_menu_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Add Menu", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        if (activeRole == "Admin") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settingsSubTab == "Menu",
                    onClick = { settingsSubTab = "Menu" },
                    label = { Text("Menu & Recipes", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_menu")
                )
                FilterChip(
                    selected = settingsSubTab == "Users",
                    onClick = { settingsSubTab = "Users" },
                    label = { Text("User Setting (Staff)", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_users")
                )
                FilterChip(
                    selected = settingsSubTab == "Cloud",
                    onClick = { settingsSubTab = "Cloud" },
                    label = { Text("Cloud Sync Settings", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_cloud")
                )
                FilterChip(
                    selected = settingsSubTab == "Data",
                    onClick = { settingsSubTab = "Data" },
                    label = { Text("Reset Or Clear", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_data")
                )
                FilterChip(
                    selected = settingsSubTab == "Printer",
                    onClick = { settingsSubTab = "Printer" },
                    label = { Text("Printer Settings", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_printer")
                )
                FilterChip(
                    selected = settingsSubTab == "Device",
                    onClick = { settingsSubTab = "Device" },
                    label = { Text("Display & Themes", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_device")
                )
                FilterChip(
                    selected = settingsSubTab == "Logo",
                    onClick = { settingsSubTab = "Logo" },
                    label = { Text("Restaurant Logo", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_logo")
                )
                FilterChip(
                    selected = settingsSubTab == "Profile",
                    onClick = { settingsSubTab = "Profile" },
                    label = { Text("Restaurant Profile", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("settings_subtab_profile")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (settingsSubTab == "Users") {
            val usersList by viewModel.users.collectAsStateWithLifecycle(initialValue = emptyList())
            
            var newUsername by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var newRole by remember { mutableStateOf("Staff") } // Default to "Staff"
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Add Staff Form Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Register New Staff Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = newUsername,
                                onValueChange = { newUsername = it },
                                label = { Text("User Name") },
                                placeholder = { Text("e.g. John Doe") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("staff_username_input"),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("Password / Code") },
                                placeholder = { Text("e.g. 1234") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("staff_password_input"),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Text(
                                text = "Select Role / Access Level:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Admin", "Cashier", "Staff").forEach { role ->
                                    val isSelected = newRole == role
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Transparent 
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { newRole = role }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = role,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    if (newUsername.isNotBlank() && newPassword.isNotBlank()) {
                                        viewModel.addUser(newUsername.trim(), newRole, newPassword.trim())
                                        newUsername = ""
                                        newPassword = ""
                                        newRole = "Staff"
                                    }
                                },
                                enabled = newUsername.isNotBlank() && newPassword.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("register_staff_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add New Staff Profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Staff directory list section
                item {
                    Text(
                        text = "Registered Staff Directory (${usersList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(usersList) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val roleColor = when (user.role) {
                                "Admin" -> MaterialTheme.colorScheme.primary
                                "Cashier" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(roleColor.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (user.role) {
                                            "Admin" -> Icons.Default.Person
                                            "Cashier" -> Icons.Default.ShoppingCart
                                            else -> Icons.Default.SoupKitchen
                                        },
                                        contentDescription = null,
                                        tint = roleColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text(
                                        text = user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = user.role.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = roleColor
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "Pass: ${user.password}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { editingUser = user },
                                    modifier = Modifier.size(36.dp).testTag("edit_user_btn_${user.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Staff member",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (user.username != "Chef Mario") {
                                    IconButton(
                                        onClick = { viewModel.deleteUser(user) },
                                        modifier = Modifier.size(36.dp).testTag("delete_user_btn_${user.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Staff member",
                                            tint = SoftAlert.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (settingsSubTab == "Cloud" && activeRole == "Admin") {
            val dbMode by viewModel.dbMode.collectAsStateWithLifecycle()
            val supabaseUrl by viewModel.supabaseUrl.collectAsStateWithLifecycle()
            val supabaseKey by viewModel.supabaseKey.collectAsStateWithLifecycle()
            val supabasePrefix by viewModel.supabasePrefix.collectAsStateWithLifecycle()
            val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

            var urlInput by remember(supabaseUrl) { mutableStateOf(supabaseUrl) }
            var keyInput by remember(supabaseKey) { mutableStateOf(supabaseKey) }
            var prefixInput by remember(supabasePrefix) { mutableStateOf(supabasePrefix) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // DB Mode Cards Selection
                Text(
                    "Database Operations Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Local Offline Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.saveSettings(context, "Offline", urlInput, keyInput, prefixInput)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (dbMode == "Offline") MaterialTheme.colorScheme.primaryContainer 
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (dbMode == "Offline") MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (dbMode == "Offline") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (dbMode == "Offline") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Offline Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                "Local SQLite engine storage on this client terminal. Works completely offline with zero latency.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Supabase Cloud Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.saveSettings(context, "Supabase", urlInput, keyInput, prefixInput)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (dbMode == "Supabase") MaterialTheme.colorScheme.primaryContainer 
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (dbMode == "Supabase") MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (dbMode == "Supabase") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (dbMode == "Supabase") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Supabase Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                "Synchronizes terminal transactions live to Supabase Cloud Server PostgreSQL for multi-tablet sync.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Cloud Connection Details Form (Only editable / visually active when Supabase mode chosen)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Supabase DB Server Credentials", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Supabase API Project URL") },
                            placeholder = { Text("https://your-proj.supabase.co") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("supabase_url_input")
                        )

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("Supabase Service / Anon API Key") },
                            placeholder = { Text("eyJhbGciOiJIUzI1NiIsIn...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("supabase_key_input")
                        )

                        OutlinedTextField(
                            value = prefixInput,
                            onValueChange = { prefixInput = it },
                            label = { Text("Database Tables Prefix") },
                            placeholder = { Text("dine_pos_") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("supabase_prefix_input")
                        )

                        // Connection Status Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (connectionStatus) {
                                        "Connected" -> DarkGreen.copy(alpha = 0.15f)
                                        "Testing..." -> AccentHoney.copy(alpha = 0.15f)
                                        "Error" -> SoftAlert.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (connectionStatus) {
                                        "Connected" -> Icons.Default.CheckCircle
                                        "Testing..." -> Icons.Default.Refresh
                                        "Error" -> Icons.Default.Error
                                        else -> Icons.Default.CloudOff
                                    }
                                    val color = when (connectionStatus) {
                                        "Connected" -> DarkGreen
                                        "Testing..." -> AccentHoney
                                        "Error" -> SoftAlert
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Status: $connectionStatus",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = color
                                    )
                                }

                                if (connectionStatus == "Connected" && dbMode == "Supabase") {
                                    Text(
                                        "LIVE CLOUD SYNC ACTIVE",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        color = DarkGreen
                                    )
                                }
                            }
                        }

                        // Operation Actions Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Test button
                            OutlinedButton(
                                onClick = { viewModel.testConnection(urlInput, keyInput) },
                                modifier = Modifier.weight(1f).height(44.dp).testTag("test_supabase_conn_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Save button
                            Button(
                                onClick = {
                                    viewModel.saveSettings(context, dbMode, urlInput, keyInput, prefixInput)
                                },
                                modifier = Modifier.weight(1.2f).height(44.dp).testTag("save_supabase_settings_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Configuration", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text(
                    "Cloud Data Synchronization",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Synchronize your local offline menu items, ingredients, sales orders, raw stock, and staff registers with the remote Supabase PostgreSQL cloud database.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val autoSyncCloud by viewModel.autoSyncCloud.collectAsStateWithLifecycle()
                        val autoPullCloud by viewModel.autoPullCloud.collectAsStateWithLifecycle()
                        val autoTwoWaySync by viewModel.autoTwoWaySync.collectAsStateWithLifecycle()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("settings_auto_sync_container")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Toggle 1: Auto Push to Cloud
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = if (autoSyncCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Auto Push to Cloud",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Automatically push local DB to cloud in background",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = autoSyncCloud,
                                        onCheckedChange = { viewModel.setAutoSyncCloud(context, it) },
                                        modifier = Modifier.testTag("settings_auto_sync_toggle")
                                    )
                                }

                                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))

                                // Toggle 2: Auto Pull from Cloud
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = if (autoPullCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Auto Pull from Cloud",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Automatically pull cloud database to local DB",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = autoPullCloud,
                                        onCheckedChange = { viewModel.setAutoPullCloud(context, it) },
                                        modifier = Modifier.testTag("settings_auto_pull_toggle")
                                    )
                                }

                                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))

                                // Toggle 3: Auto Two-Way Sync
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = if (autoTwoWaySync) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Auto Two-Way Sync",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Automatically synchronize local and cloud database",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = autoTwoWaySync,
                                        onCheckedChange = { viewModel.setAutoTwoWaySync(context, it) },
                                        modifier = Modifier.testTag("settings_auto_two_way_sync_toggle")
                                    )
                                }
                            }
                        }
                        
                        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                        
                        if (isSyncing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Text("Executing Cloud sync action...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!autoSyncCloud) {
                                        // Push to Cloud Button
                                        Button(
                                            onClick = { viewModel.pushToSupabaseCloud(context) },
                                            modifier = if (autoPullCloud) Modifier.fillMaxWidth().height(44.dp).testTag("push_supabase_btn") else Modifier.weight(1f).height(44.dp).testTag("push_supabase_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Push to Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
 
                                    if (!autoPullCloud) {
                                        // Pull from Cloud Button
                                        Button(
                                            onClick = { viewModel.pullFromSupabaseCloud(context) },
                                            modifier = if (autoSyncCloud) Modifier.fillMaxWidth().height(44.dp).testTag("pull_supabase_btn") else Modifier.weight(1f).height(44.dp).testTag("pull_supabase_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pull from Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
 
                                if (!autoTwoWaySync) {
                                    // Two-Way / Bidirectional Sync Both Sides Button
                                    Button(
                                        onClick = { viewModel.syncBothSidesSupabaseCloud(context) },
                                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("twoway_sync_supabase_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Two-Way Sync (Merge Offline & Cloud Data)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Supabase SQL Schema Copier
                var showSqlCopier by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showSqlCopier = !showSqlCopier },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Supabase SQL Schema Helper",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = if (showSqlCopier) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (showSqlCopier) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Run this schema script inside your Supabase SQL Editor to provision all tables correctly with prefix '$prefixInput':",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val sqlScript = remember(prefixInput) { com.example.data.sync.SyncBackupManager.generateSupabaseSQL(prefixInput) }
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = sqlScript,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 10,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(sqlScript) })
                                            viewModel.triggerUiMessage("SQL Script Copied to Clipboard!")
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Script", fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Or run this UPDATE script to add new columns (category & imageBase64) to an existing table:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val updateSqlScript = remember(prefixInput) { com.example.data.sync.SyncBackupManager.generateSupabaseUpdateSQL(prefixInput) }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = updateSqlScript,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 6,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(updateSqlScript) })
                                            viewModel.triggerUiMessage("Update SQL Script Copied!")
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Update SQL", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (settingsSubTab == "Data") {
            var showClearConfirm by remember { mutableStateOf(false) }
            var showResetConfirm by remember { mutableStateOf(false) }
            var backupText by remember { mutableStateOf("") }
            var restoreText by remember { mutableStateOf("") }
            var showImportConfirm by remember { mutableStateOf(false) }

            if (showImportConfirm) {
                AlertDialog(
                    onDismissRequest = { showImportConfirm = false },
                    title = { Text("Restore Database from Backup?", fontWeight = FontWeight.Bold) },
                    text = { Text("Warning: This will completely overwrite and replace your current local database with the contents of the pasted backup JSON string. This action is irreversible!") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.importBackupJson(context, restoreText) { success ->
                                    if (success) {
                                        restoreText = ""
                                        backupText = ""
                                    }
                                    showImportConfirm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Overwrite & Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    title = { Text("Clear Sales & Transactions?", fontWeight = FontWeight.Bold) },
                    text = { Text("This will delete all sales orders, customer transactions, purchase history, and batch logs. Your raw materials, menu items, and staff users will remain untouched.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllTransactions()
                                showClearConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Transactions")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    title = { Text("Complete Factory Reset Database?", fontWeight = FontWeight.Bold) },
                    text = { Text("Warning: This will wipe ALL data (including all custom menu items, raw materials, staff profiles, and sales records) and re-seed the system with default demo data.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.resetAllToDemoData()
                                showResetConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Factory Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Clear Transactional Logs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Deletes order registers, purchase invoices, and production run history. Keeps your menu, recipes, larder inventory levels, and registered user accounts intact. Perfect for starting a new fiscal period.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("clear_transactions_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Sales & Purchases Logs", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Full System Factory Reset",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Warning: This operation will completely purge the local SQLite database. All inventory, recipes, sales, purchase orders, and custom users will be permanently destroyed. The system will reboot with clean default demo seeding data.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showResetConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("factory_reset_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wipe and Re-Seed System", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Database Backup & Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Export the entire state of your bistro database (larder, recipes, sales, staff rosters) as a portable JSON text string, or paste a backup string below to restore a previous session.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                        if (isSyncing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.exportBackupJson(context) { json ->
                                            backupText = json
                                            if (json.isNotEmpty()) {
                                                clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(json) })
                                                viewModel.triggerUiMessage("Database backup copied to clipboard!")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("export_backup_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export & Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (restoreText.trim().isEmpty()) {
                                            viewModel.triggerUiMessage("Error: Please paste a valid backup JSON string first.")
                                        } else {
                                            showImportConfirm = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("import_backup_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore Database", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = restoreText,
                                onValueChange = { restoreText = it },
                                label = { Text("Paste Backup JSON Text Here", fontSize = 12.sp) },
                                placeholder = { Text("{\n  \"users\": [...],\n  \"raw_inventory\": [...]\n}", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(120.dp).testTag("restore_backup_textfield"),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                maxLines = 10,
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (backupText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Your Exported Backup (Truncated preview):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = backupText,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 6,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (settingsSubTab == "Printer") {
            PrinterSettingsSection(viewModel = viewModel)
        } else if (settingsSubTab == "Device") {
            val screenMode by viewModel.screenMode.collectAsStateWithLifecycle()
            val receiptSize by viewModel.receiptSize.collectAsStateWithLifecycle()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Screen Mode Card Preference
                Text(
                    "Display Screen Mode Preference",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Select screen mode optimization. Tablet mode enables adaptive side navigation, larger high-contrast buttons, and scaled typography suited for stationary restaurant terminals.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("Mobile", "Tablet").forEach { mode ->
                                val isSelected = screenMode == mode
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.saveScreenMode(context, mode)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = mode,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Order Receipt Size Card Preference
                Text(
                    "Order Thermal Receipt Width",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Select default thermal receipt printer width. The printed bills and sales slips will dynamically format and adjust typography wrapping, margins, and column spacing to ensure zero layout clipping.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("57mm", "80mm").forEach { size ->
                                val isSelected = receiptSize == size
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.saveReceiptSize(context, size)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = size,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Color Theme Preferences
                Text(
                    "Adjust Color Theme Selection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Tailor the POS palette. Pick between our energizing Warm Orange classic, stationary terminal Cool Blue, clean organic Forest Green, or executive Slate charcoal styles.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
                        val themes = listOf(
                            "Warm Orange" to Color(0xFFEA580C),
                            "Cool Blue" to Color(0xFF0284C7),
                            "Forest Green" to Color(0xFF059669),
                            "Classic Slate" to Color(0xFF475569)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themes.forEach { (name, color) ->
                                val isSelected = colorTheme == name
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.saveColorTheme(context, name)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = name.substringAfter(" "),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Text Size Scaling Input Card
                Text(
                    "Display Font & Interface Scale",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Set a custom layout size multiplier to scale up text and buttons for better stationary visibility. Value must be a valid positive float (e.g. 1.0 is standard, 1.25 increases size by 25%).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        var customScaleInput by remember(textScaleFactor) { mutableStateOf(textScaleFactor.toString()) }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = customScaleInput,
                                onValueChange = { customScaleInput = it },
                                label = { Text("Size Scale Factor (Multiplier)") },
                                placeholder = { Text("e.g. 1.25") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("custom_scale_input_text_field")
                            )
                            
                            Button(
                                onClick = {
                                    val factor = customScaleInput.toFloatOrNull()
                                    if (factor != null && factor > 0.1f && factor < 4.0f) {
                                        viewModel.saveTextScaleFactor(context, factor)
                                    } else {
                                        viewModel.triggerUiMessage("Error: Scale factor must be a valid float between 0.1 and 4.0")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(56.dp).testTag("save_custom_scale_button")
                            ) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = "Apply Scale")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply Size")
                            }
                        }
                    }
                }

                // 2. Hide System Bottom Navigation Bar / Immersive Fullscreen Mode
                Text(
                    "Kiosk & Immersive Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide Mobile Bottom System Navigation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hides the system 3-button navigation bar (back, home, recents) and status bar for a distraction-free full-screen restaurant terminal app.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = hideSystemBars,
                            onCheckedChange = { viewModel.saveHideSystemBars(context, it) },
                            modifier = Modifier.testTag("hide_system_bars_toggle")
                        )
                    }
                }
            }
        } else if (settingsSubTab == "Profile") {
            val rName by viewModel.restaurantName.collectAsStateWithLifecycle()
            val rSlogan by viewModel.restaurantSlogan.collectAsStateWithLifecycle()
            val rPhone by viewModel.restaurantPhone.collectAsStateWithLifecycle()
            val rAddress by viewModel.restaurantAddress.collectAsStateWithLifecycle()
            val localProfiles by viewModel.localRestaurantProfiles.collectAsStateWithLifecycle()
            val cloudProfiles by viewModel.cloudRestaurantProfiles.collectAsStateWithLifecycle()
            val isQueryingCloud by viewModel.isQueryingCloudProfiles.collectAsStateWithLifecycle()

            var editName by remember(rName) { mutableStateOf(rName) }
            var editSlogan by remember(rSlogan) { mutableStateOf(rSlogan) }
            var editPhone by remember(rPhone) { mutableStateOf(rPhone) }
            var editAddress by remember(rAddress) { mutableStateOf(rAddress) }

            // New fields for multiple profiles
            var cuisineType by remember { mutableStateOf("General") }
            var rating by remember { mutableStateOf(5.0) }
            var website by remember { mutableStateOf("") }
            var activateImmediately by remember { mutableStateOf(true) }

            var profileMode by remember { mutableStateOf("Offline") } // "Offline" or "Cloud"
            var activeShareProfile by remember { mutableStateOf<RestaurantProfile?>(null) }

            // State variables for Supabase cloud queries
            var querySearchName by remember { mutableStateOf("") }
            var querySearchCuisine by remember { mutableStateOf("") }
            var queryMinRating by remember { mutableStateOf(1.0) }
            var enableAdvancedQueryFilters by remember { mutableStateOf(false) }

            if (activeShareProfile != null) {
                val profile = activeShareProfile!!
                val shareText = """
                    🏢 PROFILE: ${profile.name}
                    ✨ SLOGAN: "${profile.slogan}"
                    🍳 CUISINE: ${profile.cuisineType} | Rating: ⭐ ${profile.rating}
                    📞 CONTACT: ${profile.phone}
                    📍 ADDRESS: ${profile.address}
                    ${if (profile.website.isNotEmpty()) "🌐 WEBSITE: ${profile.website}\n" else ""}---
                    🔗 Supabase API Query URL: ${viewModel.supabaseUrl.value}/rest/v1/${viewModel.supabasePrefix.value}restaurant_profiles?id=eq.${profile.id}
                """.trimIndent()

                AlertDialog(
                    onDismissRequest = { activeShareProfile = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Profile & Supabase Query", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Copy profile summary text or the direct Supabase API REST Query URL to share.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(profile.slogan, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("⭐ ${profile.rating} • ${profile.cuisineType}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("📍 ${profile.address}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedTextField(
                                value = shareText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            )
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Supabase REST API Query Share", "${viewModel.supabaseUrl.value}/rest/v1/${viewModel.supabasePrefix.value}restaurant_profiles?id=eq.${profile.id}")
                                    clipboard.setPrimaryClip(clip)
                                    viewModel.triggerUiMessage("Supabase API query URL copied to clipboard!")
                                    activeShareProfile = null
                                }
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy API Query Link", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Restaurant Profile Summary", shareText)
                                    clipboard.setPrimaryClip(clip)
                                    viewModel.triggerUiMessage("Profile summary text copied!")
                                    activeShareProfile = null
                                }
                            ) {
                                Icon(Icons.Default.CopyAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Text", fontSize = 11.sp)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { activeShareProfile = null }) {
                            Text("Close", fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Horizontal Sub-Tabs
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Offline Profiles", "Cloud Query & Share").forEach { mode ->
                        val isSelected = (mode == "Offline Profiles" && profileMode == "Offline") ||
                                         (mode == "Cloud Query & Share" && profileMode == "Cloud")
                        FilterChip(
                            selected = isSelected,
                            onClick = { profileMode = if (mode == "Offline Profiles") "Offline" else "Cloud" },
                            label = { Text(mode, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (mode == "Offline Profiles") Icons.Default.Storage else Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("profile_tab_" + mode.replace(" ", "_").lowercase())
                        )
                    }
                }

                if (profileMode == "Offline") {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Current Active Profile Summary Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("profile_active_summary_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Currently Active Profile",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(rName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (rSlogan.isNotEmpty()) {
                                            Text(rSlogan, style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("📞 $rPhone  •  📍 $rAddress", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        // 2. Saved Profiles List in Room
                        item {
                            Text("Local SQLite Offline Database Records", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (localProfiles.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No backup profiles found in Room database.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Fill out the form below to save multiple profiles locally.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        } else {
                            items(localProfiles.size) { index ->
                                val profile = localProfiles[index]
                                val isCurrentActive = profile.name == rName && profile.address == rAddress

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrentActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                if (profile.isActive || isCurrentActive) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp))
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Active", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = { activeShareProfile = profile },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = "Share Profile", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteLocalProfile(profile) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }

                                        if (profile.slogan.isNotEmpty()) {
                                            Text(profile.slogan, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("📞 ${profile.phone}  •  📍 ${profile.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("🍳 Cuisine: ${profile.cuisineType}  •  ⭐ Rating: ${profile.rating}  •  🌐 ${profile.website.ifEmpty { "No website" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!isCurrentActive) {
                                                Button(
                                                    onClick = { viewModel.activateLocalProfile(context, profile) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Activate", fontSize = 11.sp)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            Button(
                                                onClick = { viewModel.uploadProfileToSupabase(context, profile) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Push Cloud", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Add New Profile Form Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("profile_create_form_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Add New Profile (Local SQLite / Room)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Create and save secondary restaurant locations or profile variations into your local offline database.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text("Restaurant Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("new_profile_name")
                                    )

                                    OutlinedTextField(
                                        value = editSlogan,
                                        onValueChange = { editSlogan = it },
                                        label = { Text("Slogan / Tagline", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("new_profile_slogan")
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = editPhone,
                                            onValueChange = { editPhone = it },
                                            label = { Text("Phone Number", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("new_profile_phone")
                                        )

                                        OutlinedTextField(
                                            value = cuisineType,
                                            onValueChange = { cuisineType = it },
                                            label = { Text("Cuisine Style", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("new_profile_cuisine")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = editAddress,
                                        onValueChange = { editAddress = it },
                                        label = { Text("Address", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth().testTag("new_profile_address")
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = website,
                                            onValueChange = { website = it },
                                            label = { Text("Website (URL)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.3f).testTag("new_profile_website")
                                        )

                                        Column(modifier = Modifier.weight(0.7f)) {
                                            Text("Rating (⭐ ${String.format("%.1f", rating)})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Slider(
                                                value = rating.toFloat(),
                                                onValueChange = { rating = it.toDouble() },
                                                valueRange = 1f..5f,
                                                steps = 3,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = activateImmediately,
                                                onCheckedChange = { activateImmediately = it }
                                            )
                                            Text("Set as active profile immediately", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                if (editName.isNotBlank() && editAddress.isNotBlank()) {
                                                    viewModel.saveRestaurantProfileToRoom(
                                                        context = context,
                                                        name = editName.trim(),
                                                        slogan = editSlogan.trim(),
                                                        phone = editPhone.trim(),
                                                        address = editAddress.trim(),
                                                        cuisineType = cuisineType.trim(),
                                                        rating = rating,
                                                        website = website.trim(),
                                                        activate = activateImmediately
                                                    )
                                                } else {
                                                    viewModel.triggerUiMessage("Name and Address cannot be blank.")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("save_profile_to_room_btn")
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save Profile Offline", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Cloud Query & Share Tab (Supabase)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            var queryCategoryInput by remember { mutableStateOf("General") }
                            val cloudMenuItems by viewModel.cloudMenuItems.collectAsStateWithLifecycle()
                            val isQueryingCloudMenuItems by viewModel.isQueryingCloudMenuItems.collectAsStateWithLifecycle()
                            var sharedQueryText by remember { mutableStateOf("") }
                            var showSharedQueryDialog by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("supabase_category_query_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.04f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Category Menu Cloud Query & Share", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Text(
                                        text = "Query menu items matching a category directly from your online Supabase PostgreSQL database, or extract/share the matching SQL and API query.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = queryCategoryInput,
                                        onValueChange = { queryCategoryInput = it },
                                        label = { Text("Menu Category to Query", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("supabase_category_query_input")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.queryMenuItemsByCategoryFromSupabase(queryCategoryInput)
                                            },
                                            modifier = Modifier.weight(1.2f).testTag("query_supabase_categories_btn"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            if (isQueryingCloudMenuItems) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Querying...", fontSize = 10.sp)
                                            } else {
                                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Query Cloud Items", fontSize = 10.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val prefixVal = viewModel.supabasePrefix.value
                                                val urlVal = viewModel.supabaseUrl.value
                                                sharedQueryText = com.example.data.sync.SyncBackupManager.shareCategoryMenuQuery(prefixVal, queryCategoryInput, urlVal)
                                                
                                                // Copy to clipboard
                                                val clip = android.content.ClipData.newPlainText("Supabase Category REST Query", sharedQueryText)
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(clip)
                                                
                                                viewModel.triggerUiMessage("Category query script & API link copied to clipboard!")
                                                showSharedQueryDialog = true
                                            },
                                            modifier = Modifier.weight(1f).testTag("share_supabase_category_btn"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Share Query", fontSize = 10.sp)
                                        }
                                    }

                                    // Display matching fetched Cloud Menu Items
                                    if (cloudMenuItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Cloud Matches for '$queryCategoryInput':", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                cloudMenuItems.forEach { item ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            var bmp: androidx.compose.ui.graphics.ImageBitmap? = null
                                                            if (item.imageBase64.isNotBlank()) {
                                                                try {
                                                                    val decoded = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                                                                    val rawBmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                                                    if (rawBmp != null) {
                                                                        bmp = rawBmp.asImageBitmap()
                                                                    }
                                                                } catch (e: Exception) {}
                                                            }
                                                            if (bmp != null) {
                                                                androidx.compose.foundation.Image(
                                                                    bitmap = bmp,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                            }
                                                            Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                        }
                                                        Text("Rs. ${item.price}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Shared Query display dialog
                            if (showSharedQueryDialog) {
                                AlertDialog(
                                    onDismissRequest = { showSharedQueryDialog = false },
                                    title = { Text("Supabase Category Query Script", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("This SQL statement and direct REST API endpoint query URL have been copied to your clipboard to share with others:", fontSize = 11.sp)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                                    .padding(8.dp)
                                            ) {
                                                Text(sharedQueryText, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showSharedQueryDialog = false }) {
                                            Text("Dismiss")
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("supabase_query_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Supabase Cloud Query Builder", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        text = "Construct real-time SQL/REST queries on Supabase database to filter branch profiles by name, cuisine style, or minimum rating.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Search by Name Input Field
                                    OutlinedTextField(
                                        value = querySearchName,
                                        onValueChange = { querySearchName = it },
                                        label = { Text("Filter by Restaurant Name", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("supabase_query_search_name")
                                    )

                                    // Advanced Filters Toggle Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = enableAdvancedQueryFilters,
                                                onCheckedChange = { enableAdvancedQueryFilters = it },
                                                modifier = Modifier.testTag("enable_advanced_query_checkbox")
                                            )
                                            Text("Enable advanced query filters", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        if (querySearchName.isNotEmpty() || querySearchCuisine.isNotEmpty() || queryMinRating > 1.0) {
                                            TextButton(
                                                onClick = {
                                                    querySearchName = ""
                                                    querySearchCuisine = ""
                                                    queryMinRating = 1.0
                                                    enableAdvancedQueryFilters = false
                                                    viewModel.queryProfilesFromSupabase(context)
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Reset", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    // Advanced Filter Settings
                                    if (enableAdvancedQueryFilters) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedTextField(
                                                    value = querySearchCuisine,
                                                    onValueChange = { querySearchCuisine = it },
                                                    label = { Text("Filter by Cuisine Type", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                    modifier = Modifier.fillMaxWidth().testTag("supabase_query_cuisine")
                                                )

                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Minimum Rating (⭐ ${String.format("%.1f", queryMinRating)}+)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        if (queryMinRating > 1.0) {
                                                            TextButton(onClick = { queryMinRating = 1.0 }, contentPadding = PaddingValues(0.dp)) {
                                                                Text("Any rating", fontSize = 10.sp)
                                                            }
                                                        }
                                                    }
                                                    Slider(
                                                        value = queryMinRating.toFloat(),
                                                        onValueChange = { queryMinRating = it.toDouble() },
                                                        valueRange = 1f..5f,
                                                        steps = 3,
                                                        modifier = Modifier.fillMaxWidth().testTag("supabase_query_min_rating")
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Execute Search / Filter Query Button
                                    Button(
                                        onClick = {
                                            val queryName = querySearchName.ifBlank { null }
                                            val queryCuisine = if (enableAdvancedQueryFilters) querySearchCuisine.ifBlank { null } else null
                                            val queryRating = if (enableAdvancedQueryFilters && queryMinRating > 1.0) queryMinRating else null
                                            viewModel.queryProfilesFromSupabase(
                                                context = context,
                                                searchQuery = queryName,
                                                cuisineQuery = queryCuisine,
                                                minRatingQuery = queryRating
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("query_supabase_profiles_btn"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isQueryingCloud) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Executing Database Query...", fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (querySearchName.isNotBlank() || (enableAdvancedQueryFilters && (querySearchCuisine.isNotBlank() || queryMinRating > 1.0)))
                                                    "Search Cloud Profiles"
                                                else
                                                    "Query All Cloud Profiles",
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (cloudProfiles.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("No cloud records fetched yet.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Click the button above to query live Supabase profiles.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Supabase Cloud Query Results (${cloudProfiles.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { viewModel.queryProfilesFromSupabase(context) }) {
                                        Text("Refresh", fontSize = 11.sp)
                                    }
                                }
                            }

                            items(cloudProfiles.size) { idx ->
                                val cloudProf = cloudProfiles[idx]
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(cloudProf.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = { activeShareProfile = cloudProf },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share Profile", fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        if (cloudProf.slogan.isNotEmpty()) {
                                            Text(cloudProf.slogan, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("📞 Phone: ${cloudProf.phone}  •  📍 Address: ${cloudProf.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("🍳 Cuisine: ${cloudProf.cuisineType}  •  ⭐ Rating: ${cloudProf.rating}  •  🌐 ${cloudProf.website.ifEmpty { "No website" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.saveRestaurantProfileToRoom(
                                                        context = context,
                                                        name = cloudProf.name,
                                                        slogan = cloudProf.slogan,
                                                        phone = cloudProf.phone,
                                                        address = cloudProf.address,
                                                        cuisineType = cloudProf.cuisineType,
                                                        rating = cloudProf.rating,
                                                        website = cloudProf.website,
                                                        activate = false
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clone Offline", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (settingsSubTab == "Logo") {
            val logoLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                if (uri != null) {
                    val base64 = uriToBase64(context, uri)
                    if (base64 != null) {
                        viewModel.saveRestaurantLogo(context, base64)
                    } else {
                        viewModel.triggerUiMessage("Error converting image to logo.")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Logo Configuration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Current Logo Visual Preview
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val currentLogoBitmap = remember(logoBase64) {
                                    if (logoBase64.startsWith("PRESET:")) null else decodeBase64ToImageBitmap(logoBase64)
                                }
                                if (currentLogoBitmap != null) {
                                    Image(
                                        painter = BitmapPainter(currentLogoBitmap),
                                        contentDescription = "Current Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (logoBase64.isEmpty()) {
                                    Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_restaurant_logo),
                                        contentDescription = "Current Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val activePreset = if (logoBase64.startsWith("PRESET:")) logoBase64.substringAfter("PRESET:") else "soup_kitchen"
                                    Icon(
                                        imageVector = when (activePreset) {
                                            "soup_kitchen" -> Icons.Default.SoupKitchen
                                            "pizza" -> Icons.Default.LocalPizza
                                            "coffee" -> Icons.Default.Coffee
                                            "bakery" -> Icons.Default.BakeryDining
                                            else -> Icons.Default.LocalBar
                                        },
                                        contentDescription = "Default Logo Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (logoBase64.isNotBlank()) "Custom uploaded logo/preset is active" else "Default app icon is active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (logoBase64.isNotBlank()) DarkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { logoLauncher.launch("image/*") },
                                    enabled = activeRole == "Admin",
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("select_logo_image_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Image File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (logoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.saveRestaurantLogo(context, "") },
                                        enabled = activeRole == "Admin",
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).testTag("reset_logo_button"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftAlert),
                                        border = BorderStroke(1.dp, SoftAlert.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reset default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Preset options list
                item {
                    Text(
                        text = "Or Select Theme Preset Icon",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Select from one of our hand-crafted, high-contrast Material vectors that automatically harmonize with your chosen color theme.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val presets = listOf(
                                "Soup Kitchen" to "soup_kitchen",
                                "Pizza & Bistro" to "pizza",
                                "Cafeteria" to "coffee",
                                "Bakery Dining" to "bakery",
                                "Lounge & Bar" to "bar"
                            )

                            val activePreset = if (logoBase64.startsWith("PRESET:")) logoBase64.substringAfter("PRESET:") else ""

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEach { (name, presetCode) ->
                                    val isPresetSelected = activePreset == presetCode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isPresetSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .border(
                                                width = if (isPresetSelected) 1.5.dp else 1.dp,
                                                color = if (isPresetSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable(enabled = activeRole == "Admin") {
                                                viewModel.saveRestaurantLogo(context, "PRESET:$presetCode")
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = when (presetCode) {
                                                    "soup_kitchen" -> Icons.Default.SoupKitchen
                                                    "pizza" -> Icons.Default.LocalPizza
                                                    "coffee" -> Icons.Default.Coffee
                                                    "bakery" -> Icons.Default.BakeryDining
                                                    else -> Icons.Default.LocalBar
                                                },
                                                contentDescription = name,
                                                tint = if (isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = name.substringBefore(" "),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (menuItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No menu items registered. Added dishes appear here.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(menuItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            var bmp: androidx.compose.ui.graphics.ImageBitmap? = null
                                            if (item.imageBase64.isNotBlank()) {
                                                try {
                                                    val decoded = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                                                    val rawBmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                                    if (rawBmp != null) {
                                                        bmp = rawBmp.asImageBitmap()
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                            if (bmp != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bmp,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(6.dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = item.category,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "Rs. ${String.format("%.2f", item.price)} (+${item.taxPercent.toInt()}% Tax)",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${item.availableQuantity.toInt()} available",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )

                                        if (activeRole == "Admin") {
                                            IconButton(
                                                onClick = { viewModel.deleteMenuItem(item) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Menu Item",
                                                    tint = SoftAlert.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Recipe Status Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.hasBom) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = DarkGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "BOM Recipe Bound (${item.recipeYield.toInt()} portions standard yield)",
                                                fontSize = 11.sp,
                                                color = DarkGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "No ingredients linked yet (Standard direct sale)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Dedicated Action Button on New Line (Icon Top, Text Bottom)
                                Button(
                                    onClick = { onConfigureRecipeClick(item.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (item.hasBom) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                        contentColor = if (item.hasBom) MaterialTheme.colorScheme.onSecondaryContainer else Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("configure_recipe_btn_${item.id}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.hasBom) Icons.Default.Edit else Icons.Default.AddLink,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (item.hasBom) "Edit Dish Recipe BOM" else "Design Dish Recipe BOM",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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
}

// ==========================================
// A. CREATE CUSTOMER ORDER MODAL (POS DIALOG)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrderDialog(
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember(menuItems) {
        val list = menuItems.map { it.category }.distinct().filter { it.isNotBlank() }.toMutableList()
        if (!list.contains("General")) {
            list.add(0, "General")
        }
        list.add(0, "All")
        list
    }
    val filteredMenuItems = remember(menuItems, selectedCategory) {
        if (selectedCategory == "All") {
            menuItems
        } else {
            menuItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    var orderType by remember { mutableStateOf("Dine-In") }
    val cartQuantities = remember { mutableStateMapOf<Int, Double>() }

    // Customer profile states
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var riderName by remember { mutableStateOf("") }
    var riderPhone by remember { mutableStateOf("") }
    var riderBikeNumber by remember { mutableStateOf("") }
    var riderCharges by remember { mutableStateOf("") }
    var tableNumber by remember { mutableStateOf("Table 1") }
    var showTableDropdown by remember { mutableStateOf(false) }
    val tables = listOf("Table 1", "Table 2", "Table 3", "Table 4", "Table 5", "Table 6", "Table 7", "Table 8", "Table 9", "Table 10", "Table 12", "Table 15", "Table 20")
    var paymentMethod by remember { mutableStateOf("Cash") } // Cash, Card, UPI, Credit, Scheduled
    var creditDaysOffset by remember { mutableStateOf(7) } // 3, 7, 15, 30 days

    // Init cart with zeros
    LaunchedEffect(menuItems) {
        for (item in menuItems) {
            if (!cartQuantities.containsKey(item.id)) {
                cartQuantities[item.id] = 0.0
            }
        }
    }

    // Live mathematical calculations for user feedback in POS
    val subtotal = menuItems.sumOf { item -> (cartQuantities[item.id] ?: 0.0) * item.price }
    val tax = menuItems.sumOf { item ->
        val qty = cartQuantities[item.id] ?: 0.0
        val itemSub = qty * item.price
        itemSub * (item.taxPercent / 100.0)
    }
    val total = subtotal + tax

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Dine POS Terminal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_pos_dialog")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ticket type toggler
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    listOf("Dine-In", "Takeaway", "Online").forEach { type ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (orderType == type) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { orderType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (orderType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer Profile Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (orderType == "Dine-In") "Dine-In Table & Customer Details" else "Customer Profile & Logistics",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (orderType == "Online") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it },
                                    label = { Text("Name", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f).height(48.dp).testTag("pos_customer_name"),
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )
                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    label = { Text("Phone Number", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("pos_customer_phone"),
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                    )
                                )
                            }
                            
                            OutlinedTextField(
                                value = customerAddress,
                                onValueChange = { customerAddress = it },
                                label = { Text("Delivery Address", fontSize = 10.sp) },
                                singleLine = false,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth().height(64.dp).testTag("pos_customer_address"),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (orderType == "Dine-In") {
                                    // Table Selector Dropdown
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedCard(
                                            onClick = { showTableDropdown = true },
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pos_table_dropdown_trigger"),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.TableRestaurant,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(tableNumber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        
                                        DropdownMenu(
                                            expanded = showTableDropdown,
                                            onDismissRequest = { showTableDropdown = false },
                                            modifier = Modifier.testTag("pos_table_dropdown_menu")
                                        ) {
                                            tables.forEach { tbl ->
                                                DropdownMenuItem(
                                                    text = { Text(tbl, fontSize = 12.sp) },
                                                    onClick = {
                                                        tableNumber = tbl
                                                        showTableDropdown = false
                                                    },
                                                    modifier = Modifier.testTag("pos_table_item_$tbl")
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = customerName,
                                        onValueChange = { customerName = it },
                                        label = { Text("Customer Name", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f).height(48.dp).testTag("pos_customer_name"),
                                        textStyle = TextStyle(fontSize = 12.sp)
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = customerName,
                                        onValueChange = { customerName = it },
                                        label = { Text("Name", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f).height(48.dp).testTag("pos_customer_name"),
                                        textStyle = TextStyle(fontSize = 12.sp)
                                    )
                                    OutlinedTextField(
                                        value = customerPhone,
                                        onValueChange = { customerPhone = it },
                                        label = { Text("Phone Number", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("pos_customer_phone"),
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Scrollable Category Tabs inside POS
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("pos_category_pill_$category")
                        ) {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable menus selection list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMenuItems) { item ->
                        val qty = cartQuantities[item.id] ?: 0.0
                        val isOutOfStock = item.availableQuantity <= 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var bmp: androidx.compose.ui.graphics.ImageBitmap? = null
                                if (item.imageBase64.isNotBlank()) {
                                    try {
                                        val decoded = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                                        val rawBmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                        if (rawBmp != null) {
                                            bmp = rawBmp.asImageBitmap()
                                        }
                                    } catch (e: Exception) {}
                                }
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                    }
                                }

                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Rs. ${item.price}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${item.availableQuantity.toInt()} in stock",
                                            fontSize = 11.sp,
                                            color = if (isOutOfStock) SoftAlert else DarkGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Stepper controllers
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (qty > 0) cartQuantities[item.id] = qty - 1.0
                                    },
                                    enabled = qty > 0,
                                    modifier = Modifier.size(32.dp).testTag("pos_minus_${item.id}")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Less", modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "${qty.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )

                                IconButton(
                                    onClick = {
                                        cartQuantities[item.id] = qty + 1.0
                                    },
                                    enabled = true,
                                    modifier = Modifier.size(32.dp).testTag("pos_plus_${item.id}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "More", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Payment Method Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Payment & Credit Terms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        // Payment Methods Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("Cash", "Card", "UPI", "Credit", "Scheduled").forEach { method ->
                                val isSelected = paymentMethod == method
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { paymentMethod = method }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("payment_method_chip_$method"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val icon = when (method) {
                                            "Cash" -> Icons.Default.Payments
                                            "Card" -> Icons.Default.AccountBalanceWallet
                                            "UPI" -> Icons.Default.ShoppingCart
                                            "Credit" -> Icons.Default.AccountBalance
                                            else -> Icons.Default.Schedule
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (method == "Credit") "Customer Credit" else if (method == "Scheduled") "Scheduled" else method,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // If Credit or Scheduled, show offset options
                        if (paymentMethod == "Credit" || paymentMethod == "Scheduled") {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = if (paymentMethod == "Credit") "Select Credit Period (Days to Payment):" else "Select Schedule Duration (Days to Payment):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(3, 7, 15, 30).forEach { days ->
                                    val isOffsetSelected = creditDaysOffset == days
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isOffsetSelected) MaterialTheme.colorScheme.secondary 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isOffsetSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { creditDaysOffset = days }
                                            .padding(vertical = 6.dp)
                                            .testTag("credit_offset_$days"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$days Days",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isOffsetSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Totals checkout calculations card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", fontSize = 12.sp)
                            Text("Rs. ${String.format("%.2f", subtotal)}", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tax Calculation (VAT)", fontSize = 12.sp)
                            Text("Rs. ${String.format("%.2f", tax)}", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Ticket Amount", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Rs. ${String.format("%.2f", total)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Checkout buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val list = mutableListOf<Pair<MenuItem, Double>>()
                            for (item in menuItems) {
                                val q = cartQuantities[item.id] ?: 0.0
                                if (q > 0) {
                                    list.add(Pair(item, q))
                                }
                            }

                            val creditDueDate = if (paymentMethod == "Credit" || paymentMethod == "Scheduled") {
                                System.currentTimeMillis() + creditDaysOffset * 24L * 3600L * 1000L
                            } else {
                                null
                            }
                            val paymentStatus = if (paymentMethod == "Credit" || paymentMethod == "Scheduled") "Unpaid" else "Paid"

                            val finalCustName = if (orderType == "Dine-In") {
                                if (customerName.trim().isNotEmpty()) "$tableNumber - ${customerName.trim()}" else tableNumber
                            } else {
                                customerName.trim()
                            }

                            viewModel.placeOrder(
                                orderType = orderType,
                                orderedItemsList = list,
                                customerName = finalCustName,
                                customerPhone = customerPhone,
                                paymentMethod = paymentMethod,
                                creditDueDate = creditDueDate,
                                paymentStatus = paymentStatus,
                                customerAddress = if (orderType == "Online") customerAddress else "",
                                riderName = if (orderType == "Online") riderName else "",
                                riderPhone = if (orderType == "Online") riderPhone else "",
                                riderBikeNumber = if (orderType == "Online") riderBikeNumber else "",
                                riderCharges = if (orderType == "Online") (riderCharges.toDoubleOrNull() ?: 0.0) else 0.0
                            )
                            onDismiss()
                        },
                        enabled = total > 0,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("pos_dialog_submit_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Ticket", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// B. EXECUTE PRODUCTION BATCH RUN MODAL (DLG)
// ==========================================
@Composable
private fun ProduceBatchDialog(
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()

    val recipeItems = remember(menuItems) {
        menuItems.filter { it.hasBom }
    }

    var selectedMenuItem by remember { mutableStateOf<MenuItem?>(recipeItems.firstOrNull()) }
    var batchSizeText by remember { mutableStateOf("") }
    var showDishDropdown by remember { mutableStateOf(false) }

    // Resolve ingredients sync for real-time deficit visual check!
    var activeRecipeIngredients by remember { mutableStateOf<List<BomIngredient>>(emptyList()) }

    LaunchedEffect(selectedMenuItem) {
        selectedMenuItem?.let { item ->
            if (batchSizeText.isBlank()) {
                val defaultYield = if (item.recipeYield > 0) item.recipeYield.toInt().toString() else "1"
                batchSizeText = defaultYield
            }
            viewModel.getBomIngredientsForMenuItem(item.id).collectLatest { activeRecipeIngredients = it }
        }
    }

    val parsedBatchSize = batchSizeText.toDoubleOrNull() ?: 0.0
    val recipeYield = (selectedMenuItem?.recipeYield ?: 1.0).coerceAtLeast(0.001)
    val ratio = if (parsedBatchSize > 0) parsedBatchSize / recipeYield else 0.0

    // Real-time capacity and cost calculations
    var maxProduceablePortions = if (activeRecipeIngredients.isNotEmpty()) Double.MAX_VALUE else 0.0
    var hasAnyDeficit = false
    var deficitCount = 0
    var totalEstimatedBOMCost = 0.0

    activeRecipeIngredients.forEach { ing ->
        val rawMatch = rawMaterials.firstOrNull { it.id == ing.rawItemId }
        val currentQty = rawMatch?.quantity ?: 0.0
        val neededQty = ing.requiredQuantity * ratio
        val costPerUnit = rawMatch?.avgCostPerUnit ?: 0.0
        totalEstimatedBOMCost += neededQty * costPerUnit

        if (parsedBatchSize > 0 && currentQty < neededQty) {
            hasAnyDeficit = true
            deficitCount++
        }

        if (ing.requiredQuantity > 0) {
            val possibleWithThisRaw = (currentQty / ing.requiredQuantity) * recipeYield
            if (possibleWithThisRaw < maxProduceablePortions) {
                maxProduceablePortions = possibleWithThisRaw
            }
        }
    }
    if (maxProduceablePortions == Double.MAX_VALUE) {
        maxProduceablePortions = 0.0
    }

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
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Sticky Header Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SoupKitchen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Run BOM Kitchen Production",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Cook batch & auto-deduct raw materials",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 2. Main Content
                    if (recipeItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SoupKitchen,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No BOM Recipes Registered",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To run kitchen production, design a BOM recipe for dishes in Admin Settings > BOM Recipes.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Scrollable Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Section 1: Dish Selection
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "SELECT DISH TO PRODUCE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )

                                // Clickable Selected Dish Card with Dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showDishDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Restaurant,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = selectedMenuItem?.name ?: "Select Dish",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Recipe Yield: ${selectedMenuItem?.recipeYield?.toInt() ?: 0} units",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(
                                                            text = "In Stock: ${selectedMenuItem?.availableQuantity?.toInt() ?: 0}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select Dish",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showDishDropdown,
                                        onDismissRequest = { showDishDropdown = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .heightIn(max = 280.dp)
                                    ) {
                                        recipeItems.forEach { item ->
                                            val isSelected = selectedMenuItem?.id == item.id
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.name,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 13.sp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "Yield Ref: ${item.recipeYield.toInt()} units · Stock: ${item.availableQuantity.toInt()}",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedMenuItem = item
                                                    batchSizeText = item.recipeYield.toInt().toString()
                                                    showDishDropdown = false
                                                },
                                                modifier = Modifier.background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                                                )
                                            )
                                        }
                                    }
                                }

                                // Quick Horizontal Chip Row for Dishes
                                if (recipeItems.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        recipeItems.forEach { item ->
                                            val isSelected = selectedMenuItem?.id == item.id
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedMenuItem = item
                                                    batchSizeText = item.recipeYield.toInt().toString()
                                                },
                                                label = {
                                                    Text(
                                                        text = item.name,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                } else null,
                                                modifier = Modifier.height(32.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Section 2: Production Batch Size & Quick Portions
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "OUTPUT BATCH SIZE (PORTIONS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )

                                OutlinedTextField(
                                    value = batchSizeText,
                                    onValueChange = { batchSizeText = it },
                                    label = { Text("Portions to Produce") },
                                    placeholder = { Text("e.g. ${recipeYield.toInt()}") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SoupKitchen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (batchSizeText.isNotBlank()) {
                                            IconButton(
                                                onClick = { batchSizeText = "" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("production_batch_size_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )

                                // Quick Portion Presets
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Quick:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    SuggestionChip(
                                        onClick = { batchSizeText = recipeYield.toInt().toString() },
                                        label = { Text("1x Yield (${recipeYield.toInt()})", fontSize = 11.sp) },
                                        modifier = Modifier.height(30.dp)
                                    )

                                    SuggestionChip(
                                        onClick = { batchSizeText = (recipeYield.toInt() * 2).toString() },
                                        label = { Text("2x Yield (${recipeYield.toInt() * 2})", fontSize = 11.sp) },
                                        modifier = Modifier.height(30.dp)
                                    )

                                    SuggestionChip(
                                        onClick = {
                                            val current = batchSizeText.toDoubleOrNull() ?: 0.0
                                            batchSizeText = (current + 1).toInt().toString()
                                        },
                                        label = { Text("+1", fontSize = 11.sp) },
                                        modifier = Modifier.height(30.dp)
                                    )

                                    SuggestionChip(
                                        onClick = {
                                            val current = batchSizeText.toDoubleOrNull() ?: 0.0
                                            batchSizeText = (current + 5).toInt().toString()
                                        },
                                        label = { Text("+5", fontSize = 11.sp) },
                                        modifier = Modifier.height(30.dp)
                                    )

                                    SuggestionChip(
                                        onClick = {
                                            val current = batchSizeText.toDoubleOrNull() ?: 0.0
                                            batchSizeText = (current + 10).toInt().toString()
                                        },
                                        label = { Text("+10", fontSize = 11.sp) },
                                        modifier = Modifier.height(30.dp)
                                    )

                                    if (maxProduceablePortions.toInt() > 0) {
                                        SuggestionChip(
                                            onClick = { batchSizeText = maxProduceablePortions.toInt().toString() },
                                            label = {
                                                Text(
                                                    text = "Max Stock (${maxProduceablePortions.toInt()})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DarkGreen
                                                )
                                            },
                                            modifier = Modifier.height(30.dp)
                                        )
                                    }
                                }

                                // Inventory Capacity / Shortage Alert
                                if (parsedBatchSize > 0) {
                                    if (hasAnyDeficit) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = SoftAlert.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, SoftAlert.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = SoftAlert,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Stock Deficit: $deficitCount Ingredients Short",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SoftAlert
                                                    )
                                                    Text(
                                                        text = "Insufficient raw materials for ${parsedBatchSize.toInt()} portions. Max possible: ${maxProduceablePortions.toInt()} portions.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = DarkGreen.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, DarkGreen.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = DarkGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "All Raw Materials in Stock",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DarkGreen
                                                    )
                                                    Text(
                                                        text = "Ready to produce ${parsedBatchSize.toInt()} portions (Max possible: ${maxProduceablePortions.toInt()} portions).",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Section 3: Required Raw Materials Breakdown Card
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "REQUIRED INGREDIENTS (${activeRecipeIngredients.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.8.sp
                                    )

                                    if (parsedBatchSize > 0) {
                                        Text(
                                            text = "Est. Cost: Rs. ${String.format("%.2f", totalEstimatedBOMCost)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    if (activeRecipeIngredients.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No ingredients linked to this dish yet.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            // Subtitle info
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Scaling Factor: ${String.format("%.2f", ratio)}x",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Recipe Yield: ${recipeYield.toInt()} units",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                            activeRecipeIngredients.forEachIndexed { index, ing ->
                                                val rawMatch = rawMaterials.firstOrNull { it.id == ing.rawItemId }
                                                val currentAvailableQty = rawMatch?.quantity ?: 0.0
                                                val neededAmount = ing.requiredQuantity * ratio
                                                val isDeficit = parsedBatchSize > 0 && currentAvailableQty < neededAmount
                                                val costForThis = neededAmount * (rawMatch?.avgCostPerUnit ?: 0.0)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = ing.rawItemName,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isDeficit) SoftAlert else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Need: ${String.format("%.2f", neededAmount)} ${ing.unit}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = if (isDeficit) SoftAlert else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(
                                                                text = "Avail: ${String.format("%.2f", currentAvailableQty)} ${ing.unit}",
                                                                fontSize = 11.sp,
                                                                color = if (isDeficit) SoftAlert else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        if (rawMatch != null && rawMatch.avgCostPerUnit > 0) {
                                                            Text(
                                                                text = "Est. Cost: Rs. ${String.format("%.2f", costForThis)}",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    }

                                                    // Status Pill
                                                    if (parsedBatchSize > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(
                                                                    if (isDeficit) SoftAlert.copy(alpha = 0.15f) else DarkGreen.copy(alpha = 0.15f)
                                                                )
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isDeficit) "-${String.format("%.1f", neededAmount - currentAvailableQty)} ${ing.unit}" else "✓ In Stock",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDeficit) SoftAlert else DarkGreen
                                                            )
                                                        }
                                                    }
                                                }

                                                if (index < activeRecipeIngredients.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Sticky Bottom Action Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
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
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Close",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    selectedMenuItem?.let { item ->
                                        viewModel.produceItem(item.id, parsedBatchSize)
                                    }
                                    onDismiss()
                                },
                                enabled = parsedBatchSize > 0 && selectedMenuItem != null,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(44.dp)
                                    .testTag("production_dialog_submit_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasAnyDeficit) SoftAlert else MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SoupKitchen,
                                        contentDescription = "Run Production",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (parsedBatchSize > 0) "Produce ${parsedBatchSize.toInt()} Portions" else "Run Production",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
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

// ==========================================
// C. LOG PURCHASE RESTOCKING MODAL (DLG)
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordPurchaseDialog(
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()

    var selectedRawMaterial by remember { mutableStateOf<RawMaterial?>(rawMaterials.firstOrNull()) }
    var purchaseQtyText by remember { mutableStateOf("") }
    var purchaseCostText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Log restock purchasing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (rawMaterials.isEmpty()) {
                    Text("No inventory items found. Add ingredients first.", color = SoftAlert)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Okay")
                    }
                } else {
                    Text("Specify raw material:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(4.dp)
                            .heightIn(max = 140.dp)
                    ) {
                        LazyColumn {
                            items(rawMaterials) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedRawMaterial = item }
                                        .background(if (selectedRawMaterial?.id == item.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Stock: ${item.quantity} ${item.unit}", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                     Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = purchaseQtyText,
                            onValueChange = { purchaseQtyText = it },
                            label = { Text("Qty Inward") },
                            placeholder = { Text("e.g. 5.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_qty_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = purchaseCostText,
                            onValueChange = { purchaseCostText = it },
                            label = { Text("Bill Cost (Rs)") },
                            placeholder = { Text("e.g. 60.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_cost_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic feedback on unit pricing
                    val q = purchaseQtyText.toDoubleOrNull() ?: 0.0
                    val c = purchaseCostText.toDoubleOrNull() ?: 0.0
                    if (q > 0 && c > 0) {
                        Text(
                            text = "Buying unit cost calculation: Rs. ${String.format("%.2f", c / q)} per ${selectedRawMaterial?.unit ?: "unit"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                selectedRawMaterial?.let { rm ->
                                    viewModel.recordPurchase(rm.id, q, c)
                                }
                                onDismiss()
                            },
                            enabled = q > 0 && c > 0,
                            modifier = Modifier.testTag("purchase_submit_button")
                        ) {
                            Text("Register inward")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// D. ADD NEW RAW INVENTORY MODAL (DIALOG)
// ==========================================
@Composable
private fun AddRawMaterialDialog(
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var unitText by remember { mutableStateOf("KG") }
    var costText by remember { mutableStateOf("") }
    var thresholdText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Insert Raw Ingredient",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Ingredient name") },
                    placeholder = { Text("e.g. Tomato Sauce") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("raw_dialog_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Start Balance Qty") },
                        placeholder = { Text("e.g. 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("raw_dialog_qty_input")
                    )

                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unit") },
                        placeholder = { Text("KG") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("raw_dialog_unit_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Price per unit") },
                        placeholder = { Text("2.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("raw_dialog_cost_input")
                    )

                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("Low Stock Min") },
                        placeholder = { Text("2.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("raw_dialog_threshold_input")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val q = qtyText.toDoubleOrNull() ?: 0.0
                            val c = costText.toDoubleOrNull() ?: 0.0
                            val t = thresholdText.toDoubleOrNull() ?: 0.0
                            viewModel.addRawMaterial(nameText, q, unitText, c, t)
                            onDismiss()
                        },
                        enabled = nameText.isNotBlank(),
                        modifier = Modifier.testTag("raw_dialog_submit_button")
                    ) {
                        Text("Save Ingredient")
                    }
                }
            }
        }
    }
}

// ==========================================
// D2. EDIT RAW INVENTORY / LARDER STOCK MODAL (DIALOG)
// ==========================================
@Composable
private fun EditRawMaterialDialog(
    rawMaterial: RawMaterial,
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    var nameText by remember(rawMaterial) { mutableStateOf(rawMaterial.name) }
    var qtyText by remember(rawMaterial) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", rawMaterial.quantity)) }
    var unitText by remember(rawMaterial) { mutableStateOf(rawMaterial.unit) }
    var costText by remember(rawMaterial) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", rawMaterial.avgCostPerUnit)) }
    var thresholdText by remember(rawMaterial) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", rawMaterial.lowStockThreshold)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Edit Stock & Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Raw Materials & Larder Inventory",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            unitText.ifBlank { "UNIT" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Stock Quick Adjustment Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Current Stock Level",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it },
                            label = { Text("Current Stock (${unitText})") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("raw_dialog_edit_qty_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick delta buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val adjustments = listOf(-10.0, -1.0, +1.0, +10.0)
                            adjustments.forEach { adj ->
                                val label = if (adj > 0) "+${adj.toInt()}" else "${adj.toInt()}"
                                OutlinedButton(
                                    onClick = {
                                        val cur = qtyText.toDoubleOrNull() ?: 0.0
                                        val updated = maxOf(0.0, cur + adj)
                                        qtyText = String.format(java.util.Locale.US, "%.2f", updated)
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Ingredient Details",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Ingredient name") },
                    placeholder = { Text("e.g. Flour") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("raw_dialog_edit_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unit") },
                        placeholder = { Text("KG / L / pcs") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("raw_dialog_edit_unit_input")
                    )

                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Avg Cost / Unit (Rs.)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("raw_dialog_edit_cost_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Low Stock Alert Threshold (${unitText})") },
                    placeholder = { Text("e.g. 5.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("raw_dialog_edit_threshold_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val q = qtyText.toDoubleOrNull() ?: rawMaterial.quantity
                            val c = costText.toDoubleOrNull() ?: rawMaterial.avgCostPerUnit
                            val t = thresholdText.toDoubleOrNull() ?: rawMaterial.lowStockThreshold
                            val updated = rawMaterial.copy(
                                name = nameText.trim().ifBlank { rawMaterial.name },
                                quantity = maxOf(0.0, q),
                                unit = unitText.trim().ifBlank { rawMaterial.unit },
                                avgCostPerUnit = maxOf(0.0, c),
                                lowStockThreshold = maxOf(0.0, t)
                            )
                            viewModel.updateRawMaterial(updated)
                            onDismiss()
                        },
                        enabled = nameText.isNotBlank() && qtyText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("raw_dialog_edit_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Stock")
                    }
                }
            }
        }
    }
}

// ==========================================
// E. REGISTER NEW MENU ITEM DIALOG MODAL
// ==========================================
@Composable
private fun AddMenuItemDialog(
    viewModel: RestaurantViewModel,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var taxText by remember { mutableStateOf("10.0") }
    var recipeYieldText by remember { mutableStateOf("12.0") }
    var categoryText by remember { mutableStateOf("General") }
    var imageBase64Text by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    imageBase64Text = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Insert Menu Card Option",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Menu item / dish name") },
                    placeholder = { Text("e.g. Pepperoni Pizza") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Selling price (Rs)") },
                    placeholder = { Text("12.99") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_price_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = taxText,
                        onValueChange = { taxText = it },
                        label = { Text("Taxes %") },
                        placeholder = { Text("10.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_tax_input")
                    )

                    OutlinedTextField(
                        value = recipeYieldText,
                        onValueChange = { recipeYieldText = it },
                        label = { Text("Standard BOM Yield") },
                        placeholder = { Text("12") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_yield_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("Category menu (e.g. Pizza, Burgers, Drinks)") },
                    placeholder = { Text("General") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_category_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1.2f).testTag("select_menu_image_btn")
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (imageBase64Text.isNotBlank()) "Change Image" else "Select Image", fontSize = 11.sp)
                    }

                    var bmp: androidx.compose.ui.graphics.ImageBitmap? = null
                    if (imageBase64Text.isNotBlank()) {
                        try {
                            val decoded = android.util.Base64.decode(imageBase64Text, android.util.Base64.DEFAULT)
                            val rawBmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                            if (rawBmp != null) {
                                bmp = rawBmp.asImageBitmap()
                            }
                        } catch (e: Exception) {}
                    }
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val p = priceText.toDoubleOrNull() ?: 0.0
                            val t = taxText.toDoubleOrNull() ?: 0.0
                            val y = recipeYieldText.toDoubleOrNull() ?: 1.0
                            viewModel.addMenuItem(nameText, p, t, y, categoryText, imageBase64Text)
                            onDismiss()
                        },
                        enabled = nameText.isNotBlank(),
                        modifier = Modifier.testTag("menu_submit_button")
                    ) {
                        Text("Add to Card")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSelectionScreen(
    users: List<User>,
    logoBase64: String,
    onSelectUser: (User) -> Unit
) {
    var challengeUser by remember { mutableStateOf<User?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    if (challengeUser != null) {
        val user = challengeUser!!
        AlertDialog(
            onDismissRequest = {
                challengeUser = null
                passwordInput = ""
                passwordError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter password for ${user.username} (${user.role}):",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        placeholder = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = icon, contentDescription = "Toggle visibility")
                            }
                        },
                        isError = passwordError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("password_input_challenge"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = "Demo users default password is '123'",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput == user.password) {
                            onSelectUser(user)
                            challengeUser = null
                            passwordInput = ""
                            passwordError = null
                        } else {
                            passwordError = "Incorrect password! Please try again."
                        }
                    },
                    modifier = Modifier.testTag("submit_password_challenge")
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        challengeUser = null
                        passwordInput = ""
                        passwordError = null
                    }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            // Header Logo Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                val logoBitmap = remember(logoBase64) {
                    if (logoBase64.startsWith("PRESET:")) null else decodeBase64ToImageBitmap(logoBase64)
                }
                
                if (logoBitmap != null) {
                    Image(
                        painter = BitmapPainter(logoBitmap),
                        contentDescription = "Restaurant Logo",
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else if (logoBase64.isEmpty()) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_restaurant_logo),
                        contentDescription = "Restaurant Logo",
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    val activePreset = if (logoBase64.startsWith("PRESET:")) logoBase64.substringAfter("PRESET:") else "soup_kitchen"
                    Icon(
                        imageVector = when (activePreset) {
                            "soup_kitchen" -> Icons.Default.SoupKitchen
                            "pizza" -> Icons.Default.LocalPizza
                            "coffee" -> Icons.Default.Coffee
                            "bakery" -> Icons.Default.BakeryDining
                            else -> Icons.Default.LocalBar
                        },
                        contentDescription = "Logo Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Food Cuisine",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "ENTERPRISE MINI ERP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select your profile to access the restaurant workspace",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Profile Listing Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (users.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    users.forEach { user ->
                        val (icon, subtitle, color) = when (user.role) {
                            "Admin" -> Triple(
                                Icons.Default.Person,
                                "Full administrative & catalog access",
                                MaterialTheme.colorScheme.primary
                            )
                            "Cashier" -> Triple(
                                Icons.Default.ShoppingCart,
                                "Place customer sales & handle tickets",
                                MaterialTheme.colorScheme.secondary
                            )
                            else -> Triple(
                                Icons.Default.SoupKitchen,
                                "Monitor ingredients & process production batches",
                                MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clickable { challengeUser = user }
                                .testTag("login_profile_${user.role.lowercase()}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user.role.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = color,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Food Cuisine Mini ERP v1.2 • Protected Client Session",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun generateCustomSalesReportHtml(
    startDateLong: Long,
    endDateLong: Long,
    filteredOrders: List<Order>,
    totalSales: Double,
    completedCount: Int,
    averageValue: Double,
    paymentBreakdown: Map<String, Double>
): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val startStr = sdf.format(Date(startDateLong))
    val endStr = sdf.format(Date(endDateLong))
    val reportDateStr = sdf.format(Date())

    val ordersHtml = filteredOrders.map { ord ->
        val ordDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(ord.orderDate))
        val custStr = if (ord.customerName.isNotEmpty()) "${ord.customerName} (${ord.customerPhone})" else "Walk-In"
        """
        <tr>
            <td>#${ord.orderNumber}</td>
            <td>$ordDate</td>
            <td>$custStr</td>
            <td>${ord.type}</td>
            <td>${ord.paymentMethod ?: "Cash"}</td>
            <td>${ord.paymentStatus}</td>
            <td style="text-align: right; font-weight: bold;">Rs. ${String.format("%.2f", ord.totalAmount)}</td>
        </tr>
        """.trimIndent()
    }.joinToString("\n")

    val paymentBreakdownHtml = paymentBreakdown.map { (method, amt) ->
        """
        <div style="display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px dashed #eee;">
            <span style="font-size: 13px; color: #555;">$method:</span>
            <span style="font-size: 13px; font-weight: bold;">Rs. ${String.format("%.2f", amt)}</span>
        </div>
        """.trimIndent()
    }.joinToString("\n")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 24px; color: #333; line-height: 1.4; }
                .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #333; padding-bottom: 15px; }
                .title { font-size: 24px; font-weight: bold; margin: 0; text-transform: uppercase; letter-spacing: 1px; }
                .subtitle { font-size: 12px; color: #666; margin: 5px 0 0 0; }
                .meta { display: flex; justify-content: space-between; font-size: 12px; color: #444; margin-bottom: 20px; }
                .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; margin-bottom: 25px; }
                .card { background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 15px; text-align: center; }
                .card-label { font-size: 10px; font-weight: bold; color: #6c757d; text-transform: uppercase; margin-bottom: 5px; }
                .card-val { font-size: 20px; font-weight: bold; color: #212529; }
                .table-container { margin-top: 20px; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th { background-color: #f1f3f5; text-align: left; padding: 10px; font-size: 11px; font-weight: bold; text-transform: uppercase; border-bottom: 2px solid #dee2e6; }
                td { padding: 10px; font-size: 12px; border-bottom: 1px solid #dee2e6; }
                tr:nth-child(even) { background-color: #f8f9fa; }
                .breakdown-container { background: #fdfdfd; border: 1px solid #eee; border-radius: 8px; padding: 15px; margin-top: 20px; max-width: 350px; }
                .footer { text-align: center; margin-top: 40px; font-size: 10px; color: #999; border-top: 1px solid #eee; padding-top: 15px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1 class="title">Food Cuisine Sales Report</h1>
                <p class="subtitle">Custom Date Range Analytics</p>
            </div>
            
            <div class="meta">
                <div><strong>Period:</strong> $startStr to $endStr</div>
                <div><strong>Generated:</strong> $reportDateStr</div>
            </div>
            
            <div class="grid">
                <div class="card">
                    <div class="card-label">Total Revenue</div>
                    <div class="card-val" style="color: #2b8a3e;">Rs. ${String.format("%.2f", totalSales)}</div>
                </div>
                <div class="card">
                    <div class="card-label">Orders Settled</div>
                    <div class="card-val">$completedCount</div>
                </div>
                <div class="card">
                    <div class="card-label">Avg Ticket Size</div>
                    <div class="card-val">Rs. ${String.format("%.2f", averageValue)}</div>
                </div>
            </div>
            
            <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 20px;">
                <div class="breakdown-container" style="flex: 1;">
                    <h3 style="font-size: 13px; margin: 0 0 10px 0; color: #333; text-transform: uppercase; border-bottom: 1px solid #eee; padding-bottom: 5px;">Payment Mode Summary</h3>
                    $paymentBreakdownHtml
                </div>
            </div>
            
            <div class="table-container">
                <h3 style="font-size: 13px; margin: 25px 0 10px 0; color: #333; text-transform: uppercase;">Sales Transaction Ledger</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Order #</th>
                            <th>Date & Time</th>
                            <th>Customer Contact</th>
                            <th>Type</th>
                            <th>Method</th>
                            <th>Status</th>
                            <th style="text-align: right;">Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        $ordersHtml
                    </tbody>
                </table>
            </div>
            
            <div class="footer">
                <p>System Powered by Dine POS Terminal Central DB Integration</p>
            </div>
        </body>
        </html>
    """.trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidersTab(
    viewModel: RestaurantViewModel
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle(emptyList())
    
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Pending", "Out for Delivery", "Delivered"
    var searchQuery by remember { mutableStateOf("") }

    // Real-time ticking state for stopwatch
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // Filtered orders
    val onlineOrders = remember(orders, selectedFilter, searchQuery) {
        orders.filter { order ->
            order.type == "Online" &&
            (searchQuery.isEmpty() || 
             order.orderNumber.contains(searchQuery, ignoreCase = true) || 
             order.customerName.contains(searchQuery, ignoreCase = true) || 
             order.riderName.contains(searchQuery, ignoreCase = true)) &&
            when (selectedFilter) {
                "Pending" -> order.riderName.isEmpty() && order.status != "Completed" && order.status != "Cancelled"
                "Out for Delivery" -> order.riderName.isNotEmpty() && order.status != "Completed" && order.status != "Cancelled"
                "Delivered" -> order.status == "Completed"
                else -> true
            }
        }.sortedByDescending { it.orderDate }
    }

    val totalCount = remember(orders) { orders.count { it.type == "Online" } }
    val pendingCount = remember(orders) { orders.count { it.type == "Online" && it.riderName.isEmpty() && it.status != "Completed" && it.status != "Cancelled" } }
    val outCount = remember(orders) { orders.count { it.type == "Online" && it.riderName.isNotEmpty() && it.status != "Completed" && it.status != "Cancelled" } }
    val deliveredCount = remember(orders) { orders.count { it.type == "Online" && it.status == "Completed" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("riders_tab_root")
    ) {
        // Title block
        Column(modifier = Modifier.fillMaxWidth()) {
            Column {
                Icon(
                    imageVector = Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Riders & Delivery Logistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Track online orders, manage riders, calculate delivery charges, and monitor trip duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Total Online",
                value = "$totalCount",
                icon = Icons.Default.Language,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pending Rider",
                value = "$pendingCount",
                icon = Icons.Default.Warning,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "On the Road",
                value = "$outCount",
                icon = Icons.Default.DirectionsBike,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Delivered",
                value = "$deliveredCount",
                icon = Icons.Default.CheckCircle,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter chips and search query
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Pending", "Out for Delivery", "Delivered").forEach { filter ->
                    CustomFilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = filter
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Order / Customer / Rider", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.width(280.dp).height(40.dp),
                textStyle = TextStyle(fontSize = 11.sp),
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Deliveries List
        if (onlineOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No online orders matched the selected filter.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(onlineOrders, key = { it.id }) { order ->
                    RiderOrderCard(
                        order = order,
                        viewModel = viewModel,
                        currentTime = currentTime
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RiderOrderCard(
    order: Order,
    viewModel: RestaurantViewModel,
    currentTime: Long
) {
    var isEditing by remember { mutableStateOf(false) }
    
    // Inputs for Rider details
    var riderNameInput by remember { mutableStateOf(order.riderName) }
    var riderPhoneInput by remember { mutableStateOf(order.riderPhone) }
    var riderBikeInput by remember { mutableStateOf(order.riderBikeNumber) }
    var riderChargesInput by remember { mutableStateOf(if (order.riderCharges > 0) order.riderCharges.toString() else "") }

    LaunchedEffect(order) {
        riderNameInput = order.riderName
        riderPhoneInput = order.riderPhone
        riderBikeInput = order.riderBikeNumber
        riderChargesInput = if (order.riderCharges > 0) order.riderCharges.toString() else ""
    }

    // Elapsed Duration Calculator
    val elapsedMs = currentTime - order.orderDate
    val elapsedMins = elapsedMs / 60000
    val elapsedSecs = (elapsedMs % 60000) / 1000
    val formattedDuration = if (order.status == "Completed") "Completed" else "${elapsedMins}m ${elapsedSecs}s"
    
    val durationColor = when {
        order.status == "Completed" -> MaterialTheme.colorScheme.tertiary
        elapsedMins >= 30 -> MaterialTheme.colorScheme.error
        elapsedMins >= 15 -> Color(0xFFF2994A) // Orange warning
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("rider_order_card_${order.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (order.status == "Completed") MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (order.riderName.isEmpty() && order.status != "Completed" && order.status != "Cancelled") {
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = order.orderNumber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Order Placed Date
                    val dateFormat = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
                    val formattedTime = remember(order.orderDate) { dateFormat.format(java.util.Date(order.orderDate)) }
                    Text(
                        text = "Placed at $formattedTime",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Active Time Duration / Completed Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(durationColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = durationColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (order.status == "Completed") "Delivered" else "Duration: $formattedDuration",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = durationColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Layout: Customer Details & Rider Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: Customer & Address Information
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = "CUSTOMER INFO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = order.customerName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (order.customerPhone.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = order.customerPhone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (order.customerAddress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = order.customerAddress,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                style = TextStyle(lineHeight = 14.sp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "COD Total Amount: Rs. ${String.format("%.2f", order.totalAmount)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(100.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // Right: Rider Logistics Information
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RIDER INFORMATION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        // Clear Rider Details Button ("remove rider detail in online order portion")
                        if (order.riderName.isNotEmpty() && order.status != "Completed" && !isEditing) {
                            IconButton(
                                onClick = {
                                    viewModel.updateRiderDetails(order.id, "", "", "", 0.0)
                                    // Reset local states
                                    riderNameInput = ""
                                    riderPhoneInput = ""
                                    riderBikeInput = ""
                                    riderChargesInput = ""
                                },
                                modifier = Modifier.size(24.dp).testTag("clear_rider_button_${order.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Rider",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = riderNameInput,
                                    onValueChange = { riderNameInput = it },
                                    label = { Text("Rider Name", fontSize = 9.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(fontSize = 11.sp)
                                )
                                OutlinedTextField(
                                    value = riderPhoneInput,
                                    onValueChange = { riderPhoneInput = it },
                                    label = { Text("Phone", fontSize = 9.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = riderBikeInput,
                                    onValueChange = { riderBikeInput = it },
                                    label = { Text("Bike No", fontSize = 9.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(fontSize = 11.sp)
                                )
                                OutlinedTextField(
                                    value = riderChargesInput,
                                    onValueChange = { riderChargesInput = it },
                                    label = { Text("Charges", fontSize = 9.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f),
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { 
                                        isEditing = false
                                        riderNameInput = order.riderName
                                        riderPhoneInput = order.riderPhone
                                        riderBikeInput = order.riderBikeNumber
                                        riderChargesInput = if (order.riderCharges > 0) order.riderCharges.toString() else ""
                                    },
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Cancel", fontSize = 9.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateRiderDetails(
                                            order.id,
                                            riderNameInput.trim(),
                                            riderPhoneInput.trim(),
                                            riderBikeInput.trim(),
                                            riderChargesInput.toDoubleOrNull() ?: 0.0
                                        )
                                        isEditing = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Save", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        if (order.riderName.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Rider Pending", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { isEditing = true },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp).testTag("assign_rider_button_${order.id}")
                                    ) {
                                        Text("Assign Rider", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Rider assigned info display
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(imageVector = Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(text = order.riderName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (order.riderPhone.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = order.riderPhone, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (order.riderBikeNumber.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "Bike: ${order.riderBikeNumber}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "Charges: Rs. ${String.format("%.2f", order.riderCharges)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (order.status != "Completed") {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    OutlinedButton(
                                        onClick = { isEditing = true },
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp).testTag("edit_rider_button_${order.id}")
                                    ) {
                                        Text("Change Rider", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action row
            if (order.status != "Completed" && order.status != "Cancelled") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action button based on rider details assignment
                    if (order.riderName.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.updateOrderStatus(order.id, "Completed")
                                viewModel.updateOrderPaymentStatus(order.id, "Paid")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp).testTag("mark_delivered_button_${order.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm Delivered & Paid", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Please assign a rider before dispatching delivery",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        kotlinx.coroutines.delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(1000)) + expandVertically(animationSpec = androidx.compose.animation.core.tween(1000)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(500))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_restaurant_logo),
                        contentDescription = "Food Cuisine Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Food Cuisine",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "HOUSE OF TASTE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4B400),
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
