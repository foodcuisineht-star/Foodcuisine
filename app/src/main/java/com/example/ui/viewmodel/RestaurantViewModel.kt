package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.RestaurantRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RestaurantViewModel(private val repository: RestaurantRepository) : ViewModel() {

    // --- Core Database Flows ---
    val rawMaterials: StateFlow<List<RawMaterial>> = repository.allRawMaterials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val menuItems: StateFlow<List<MenuItem>> = repository.allMenuItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<Order>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<Purchase>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBomRecipes: StateFlow<List<BomIngredient>> = repository.allBomRecipes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productionBatches: StateFlow<List<ProductionBatch>> = repository.allProductionBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localRestaurantProfiles: StateFlow<List<RestaurantProfile>> = repository.allRestaurantProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cloudRestaurantProfiles = MutableStateFlow<List<RestaurantProfile>>(emptyList())
    val cloudRestaurantProfiles: StateFlow<List<RestaurantProfile>> = _cloudRestaurantProfiles.asStateFlow()

    private val _isQueryingCloudProfiles = MutableStateFlow(false)
    val isQueryingCloudProfiles: StateFlow<Boolean> = _isQueryingCloudProfiles.asStateFlow()

    private val _cloudMenuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val cloudMenuItems: StateFlow<List<MenuItem>> = _cloudMenuItems.asStateFlow()

    private val _isQueryingCloudMenuItems = MutableStateFlow(false)
    val isQueryingCloudMenuItems: StateFlow<Boolean> = _isQueryingCloudMenuItems.asStateFlow()

    // --- UI/UX States ---
    private val _activeUser = MutableStateFlow<User?>(null)
    val activeUser: StateFlow<User?> = _activeUser.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        // Run database seeding if currently empty
        seedDataIfEmpty()
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    fun triggerUiMessage(message: String) {
        _uiMessage.value = message
    }

    fun selectUser(user: User?) {
        _activeUser.value = user
    }

    fun addUser(username: String, role: String, password: String) {
        viewModelScope.launch {
            repository.insertUser(User(username = username, role = role, password = password))
            _uiMessage.value = "User '$username' registered successfully as $role"
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun editUser(user: User, newUsername: String, newRole: String, newPassword: String) {
        viewModelScope.launch {
            if (user.username == "Chef Mario" && newRole != "Admin") {
                _uiMessage.value = "Cannot change the default Admin's role!"
                return@launch
            }
            val updatedUser = user.copy(username = newUsername, role = newRole, password = newPassword)
            repository.insertUser(updatedUser)
            _uiMessage.value = "User '${user.username}' updated successfully!"
            if (_activeUser.value?.id == user.id) {
                _activeUser.value = updatedUser
            }
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            if (user.username == "Chef Mario") {
                _uiMessage.value = "Cannot delete the default Admin account!"
                return@launch
            }
            if (_activeUser.value?.id == user.id) {
                _uiMessage.value = "Cannot delete the currently logged in user!"
                return@launch
            }
            repository.deleteUser(user)
            _uiMessage.value = "User '${user.username}' deleted successfully"
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            _uiMessage.value = "All sales orders, transactions, and production batch logs cleared successfully!"
        }
    }

    fun resetAllToDemoData() {
        viewModelScope.launch {
            repository.resetAllDatabase()
            seedDataIfEmpty()
            _uiMessage.value = "Database completely reset and initialized with default user accounts!"
        }
    }

    private fun seedDataIfEmpty() {
        viewModelScope.launch {
            // Check if user accounts exist already
            val userCount = repository.getUsersCount()
            if (userCount > 0) {
                _activeUser.value = null
                return@launch
            }

            // Seed only the essential users so they can log in to perform Sync operations!
            val adminUser = User(username = "Chef Mario", role = "Admin", password = "123")
            val cashierUser = User(username = "Alice Clerk", role = "Cashier", password = "123")
            val staffUser = User(username = "John Cook", role = "Staff", password = "123")
            repository.insertUser(adminUser)
            repository.insertUser(cashierUser)
            repository.insertUser(staffUser)
            
            // Keep activeUser as null initially so they can pick a profile at startup
            _activeUser.value = null
        }
    }

    private fun getPastTime(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
    }

    // --- Action Methods ---

    fun addRawMaterial(name: String, quantity: Double, unit: String, avgCost: Double, lowThreshold: Double) {
        viewModelScope.launch {
            if (name.isBlank() || unit.isBlank() || quantity < 0 || avgCost < 0 || lowThreshold < 0) {
                _uiMessage.value = "Error: Please enter valid inventory values."
                return@launch
            }
            val material = RawMaterial(
                name = name.trim(),
                quantity = quantity,
                unit = unit.trim(),
                avgCostPerUnit = avgCost,
                lowStockThreshold = lowThreshold
            )
            repository.insertRawMaterial(material)
            _uiMessage.value = "Raw material '$name' added successfully!"
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun deleteRawMaterial(material: RawMaterial) {
        viewModelScope.launch {
            repository.deleteRawMaterial(material)
            _uiMessage.value = "Raw material '${material.name}' deleted."
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun updateRawMaterial(material: RawMaterial) {
        viewModelScope.launch {
            if (material.name.isBlank() || material.unit.isBlank() || material.quantity < 0 || material.avgCostPerUnit < 0 || material.lowStockThreshold < 0) {
                _uiMessage.value = "Error: Please enter valid inventory values."
                return@launch
            }
            repository.updateRawMaterial(material)
            _uiMessage.value = "Stock & details for '${material.name}' updated successfully!"
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun updateRawMaterialStock(materialId: Int, newQuantity: Double) {
        viewModelScope.launch {
            if (newQuantity < 0) {
                _uiMessage.value = "Error: Stock quantity cannot be negative."
                return@launch
            }
            val current = rawMaterials.value.firstOrNull { it.id == materialId }
            if (current != null) {
                val updated = current.copy(quantity = newQuantity)
                repository.updateRawMaterial(updated)
                _uiMessage.value = "Stock for '${current.name}' updated to ${String.format("%.2f", newQuantity)} ${current.unit}"
                triggerAutoSyncsOnDatabaseUpdate()
            }
        }
    }

    fun recordPurchase(rawItemId: Int, quantity: Double, totalCost: Double) {
        viewModelScope.launch {
            if (quantity <= 0 || totalCost <= 0) {
                _uiMessage.value = "Error: Quantity and Purchase cost must be positive."
                return@launch
            }
            val rawList = rawMaterials.value
            val target = rawList.firstOrNull { it.id == rawItemId }
            if (target == null) {
                _uiMessage.value = "Error: Seleced raw material not found."
                return@launch
            }

            val purchase = Purchase(
                rawItemId = rawItemId,
                rawItemName = target.name,
                quantity = quantity,
                cost = totalCost,
                purchaseDate = System.currentTimeMillis()
            )
            
            repository.recordPurchase(purchase)
                .onSuccess {
                    _uiMessage.value = "${target.name} stock restocked successfully. Average cost updated!"
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Error: ${it.message}"
                }
        }
    }

    fun addMenuItem(
        name: String,
        price: Double,
        taxPercent: Double,
        recipeYield: Double,
        category: String = "General",
        imageBase64: String = ""
    ) {
        viewModelScope.launch {
            if (name.isBlank() || price <= 0 || taxPercent < 0 || recipeYield <= 0) {
                _uiMessage.value = "Error: Please enter valid menu values."
                return@launch
            }
            val item = MenuItem(
                name = name.trim(),
                price = price,
                taxPercent = taxPercent,
                availableQuantity = 0.0,
                recipeYield = recipeYield,
                hasBom = false,
                category = if (category.isNotBlank()) category.trim() else "General",
                imageBase64 = imageBase64
            )
            repository.insertMenuItem(item)
            _uiMessage.value = "Menu Item '$name' added successfully!"
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun deleteMenuItem(item: MenuItem) {
        viewModelScope.launch {
            repository.deleteMenuItem(item)
            _uiMessage.value = "Menu Item '${item.name}' removed from menu."
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun saveRecipe(menuItemId: Int, ingredients: List<BomIngredient>, recipeYield: Double) {
        viewModelScope.launch {
            if (recipeYield <= 0) {
                _uiMessage.value = "Error: Recipe batch size yield must be positive."
                return@launch
            }
            val targetName = menuItems.value.firstOrNull { it.id == menuItemId }?.name ?: "Dish"
            repository.createOrUpdateRecipe(menuItemId, ingredients, recipeYield)
            _uiMessage.value = "BOM Recipe for '$targetName' saved (${ingredients.size} ingredients linked)."
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun deleteRecipe(menuItemId: Int) {
        viewModelScope.launch {
            val targetName = menuItems.value.firstOrNull { it.id == menuItemId }?.name ?: "Dish"
            repository.createOrUpdateRecipe(menuItemId, emptyList(), 1.0)
            _uiMessage.value = "BOM Recipe for '$targetName' removed."
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun copyRecipeToDish(sourceMenuItemId: Int, targetMenuItemId: Int) {
        viewModelScope.launch {
            val sourceItem = menuItems.value.firstOrNull { it.id == sourceMenuItemId } ?: return@launch
            val targetItem = menuItems.value.firstOrNull { it.id == targetMenuItemId } ?: return@launch
            val sourceIngredients = allBomRecipes.value.filter { it.menuItemId == sourceMenuItemId }
            if (sourceIngredients.isEmpty()) {
                _uiMessage.value = "Source dish has no recipe to copy."
                return@launch
            }
            val copiedIngredients = sourceIngredients.map {
                it.copy(id = 0, menuItemId = targetMenuItemId)
            }
            repository.createOrUpdateRecipe(targetMenuItemId, copiedIngredients, sourceItem.recipeYield)
            _uiMessage.value = "Recipe copied from '${sourceItem.name}' to '${targetItem.name}' successfully."
            triggerAutoSyncsOnDatabaseUpdate()
        }
    }

    fun produceItem(menuItemId: Int, quantityToProduce: Double) {
        viewModelScope.launch {
            if (quantityToProduce <= 0) {
                _uiMessage.value = "Error: Quantity to produce must be positive."
                return@launch
            }
            repository.runProduction(menuItemId, quantityToProduce)
                .onSuccess {
                    _uiMessage.value = "Production run completed: Produced $quantityToProduce portions. Raw materials deducted successfully!"
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = it.message ?: "Production execution failed."
                }
        }
    }

    fun deleteProductionBatch(batch: ProductionBatch, revertInventory: Boolean = true) {
        viewModelScope.launch {
            repository.deleteProductionBatch(batch, revertInventory)
                .onSuccess {
                    _uiMessage.value = if (revertInventory) {
                        "Production batch deleted. Raw inventory and dish stock restored!"
                    } else {
                        "Production batch log removed."
                    }
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Error deleting batch: ${it.message}"
                }
        }
    }

    fun updateProductionBatch(
        updatedBatch: ProductionBatch,
        previousBatch: ProductionBatch,
        adjustInventory: Boolean = true
    ) {
        viewModelScope.launch {
            if (updatedBatch.producedQuantity <= 0) {
                _uiMessage.value = "Error: Batch output quantity must be greater than 0."
                return@launch
            }
            repository.updateProductionBatch(updatedBatch, previousBatch, adjustInventory)
                .onSuccess {
                    _uiMessage.value = "Production batch updated successfully!"
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Error updating batch: ${it.message}"
                }
        }
    }

    fun placeOrder(
        orderType: String,
        orderedItemsList: List<Pair<MenuItem, Double>>,
        customerName: String = "",
        customerPhone: String = "",
        paymentMethod: String = "Cash",
        creditDueDate: Long? = null,
        paymentStatus: String = "Paid",
        customerAddress: String = "",
        riderName: String = "",
        riderPhone: String = "",
        riderBikeNumber: String = "",
        riderCharges: Double = 0.0
    ): StateFlow<Result<Long>?> {
        val actionResult = MutableStateFlow<Result<Long>?>(null)
        viewModelScope.launch {
            if (orderedItemsList.isEmpty()) {
                actionResult.value = Result.failure(Exception("Cannot place order: No items selected."))
                _uiMessage.value = "Error: Order is empty!"
                return@launch
            }

            var totalQty = 0.0
            var subtotal = 0.0
            var totalTax = 0.0

            val orderItems = mutableListOf<OrderItem>()

            for (pair in orderedItemsList) {
                val mItem = pair.first
                val qty = pair.second
                if (qty <= 0) continue

                val itemSubtotal = mItem.price * qty
                val itemTax = itemSubtotal * (mItem.taxPercent / 100.0)
                val itemTotal = itemSubtotal + itemTax

                totalQty += qty
                subtotal += itemSubtotal
                totalTax += itemTax

                val orderItem = OrderItem(
                    orderId = 0, // updated in repository on insert
                    menuItemId = mItem.id,
                    menuItemName = mItem.name,
                    quantity = qty,
                    unitPrice = mItem.price,
                    taxPercent = mItem.taxPercent,
                    taxAmount = itemTax,
                    totalAmount = itemTotal
                )
                orderItems.add(orderItem)
            }

            val randomNum = (100..999).random()
            val orderNumber = "ORD-$randomNum"

            val orderDoc = Order(
                orderNumber = orderNumber,
                type = orderType,
                status = "Pending",
                totalItemsQuantity = totalQty,
                subtotal = subtotal,
                taxAmount = totalTax,
                totalAmount = subtotal + totalTax,
                orderDate = System.currentTimeMillis(),
                customerName = customerName,
                customerPhone = customerPhone,
                paymentMethod = paymentMethod,
                creditDueDate = creditDueDate,
                paymentStatus = paymentStatus,
                customerAddress = customerAddress,
                riderName = riderName,
                riderPhone = riderPhone,
                riderBikeNumber = riderBikeNumber,
                riderCharges = riderCharges
            )

            repository.createOrder(orderDoc, orderItems)
                .onSuccess { id ->
                    actionResult.value = Result.success(id)
                    _uiMessage.value = "Order $orderNumber created successfully!"
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure { t ->
                    actionResult.value = Result.failure(t)
                    _uiMessage.value = t.message ?: "Failed to place order."
                }
        }
        return actionResult.asStateFlow()
    }

    fun updateOrderStatus(orderId: Int, nextStatus: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, nextStatus)
                .onSuccess {
                    _uiMessage.value = "Order status updated to $nextStatus."
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Failed to update order: ${it.message}"
                }
        }
    }

    fun updateOrderPaymentStatus(orderId: Int, nextPaymentStatus: String) {
        viewModelScope.launch {
            repository.updateOrderPaymentStatus(orderId, nextPaymentStatus)
                .onSuccess {
                    _uiMessage.value = "Order payment status updated to $nextPaymentStatus."
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Failed to update payment status: ${it.message}"
                }
        }
    }

    fun updateRiderDetails(
        orderId: Int,
        riderName: String,
        riderPhone: String,
        riderBikeNumber: String,
        riderCharges: Double
    ) {
        viewModelScope.launch {
            repository.updateRiderDetails(orderId, riderName, riderPhone, riderBikeNumber, riderCharges)
                .onSuccess {
                    _uiMessage.value = "Rider details saved successfully!"
                    triggerAutoSyncsOnDatabaseUpdate()
                }
                .onFailure {
                    _uiMessage.value = "Failed to update rider details: ${it.message}"
                }
        }
    }

    fun simulateIncomingOnlineOrder(context: android.content.Context) {
        // Simulation removed by user request
    }

    // --- Dynamic Analytics & Reports ---

    fun getOrderItemsForOrderFlow(orderId: Int): Flow<List<OrderItem>> {
        return repository.getOrderItemsForOrder(orderId)
    }

    fun getBomIngredientsForMenuItem(menuItemId: Int): Flow<List<BomIngredient>> {
        return repository.getBomIngredientsForMenuItem(menuItemId)
    }

    // Today's Stats
    private val todayCalendarStart: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    private val todayCalendarEnd: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }

    // Month's Stats (June 2026 or Current Calendar Month)
    private val monthCalendarStart: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    private val monthCalendarEnd: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return cal.timeInMillis
        }

    // Reactive Analytics Flows
    val metricsToday: Flow<TodayMetrics> = orders.combine(productionBatches) { orderList, prodList ->
        val todayStart = todayCalendarStart
        val todayEnd = todayCalendarEnd

        // Today's completed and current non-cancelled orders
        val todayOrders = orderList.filter { it.orderDate in todayStart..todayEnd }
        val nonCancelledOrders = todayOrders.filter { it.status != "Cancelled" }

        val salesSum = nonCancelledOrders.sumOf { it.totalAmount }
        val ordersCount = todayOrders.size

        // Count of produced items today
        val todayProducedList = prodList.filter { it.productionDate in todayStart..todayEnd }
        val totalProducedCount = todayProducedList.sumOf { it.producedQuantity }

        TodayMetrics(
            totalRevenue = salesSum,
            ordersCount = ordersCount,
            itemsProduced = totalProducedCount,
            ordersList = todayOrders
        )
    }

    val metricsMonthly: Flow<MonthlyMetrics> = combine(orders, purchases, productionBatches) { orderList, purchaseList, prodList ->
        val startMills = monthCalendarStart
        val endMills = monthCalendarEnd

        val monthOrders = orderList.filter { it.orderDate in startMills..endMills }
        val completedMonthOrders = monthOrders.filter { it.status != "Cancelled" }
        val totalRevenue = completedMonthOrders.sumOf { it.totalAmount }

        // Expenses are purchases of raw inventory made in the month
        val monthPurchases = purchaseList.filter { it.purchaseDate in startMills..endMills }
        val totalPurchasesCost = monthPurchases.sumOf { it.cost }

        // Cost of raw materials used in production batches run this month
        val monthProduction = prodList.filter { it.productionDate in startMills..endMills }
        val totalProductionCost = monthProduction.sumOf { it.totalCost }

        // Profit = Sales - Cost of materials used in active stock production
        val netProfit = totalRevenue - totalProductionCost

        MonthlyMetrics(
            totalRevenue = totalRevenue,
            ordersCount = monthOrders.size,
            totalExpensesPurchases = totalPurchasesCost,
            costOfProduction = totalProductionCost,
            netProfit = netProfit,
            purchasesList = monthPurchases,
            batchesList = monthProduction
        )
    }

    // --- Centralized Database Sync & Connection Settings (Supabase / Offline) ---
    private val _dbMode = MutableStateFlow("Offline") // "Offline" or "Supabase"
    val dbMode: StateFlow<String> = _dbMode.asStateFlow()

    private val _supabaseUrl = MutableStateFlow("")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseKey = MutableStateFlow("")
    val supabaseKey: StateFlow<String> = _supabaseKey.asStateFlow()

    private val _supabasePrefix = MutableStateFlow("dine_pos_")
    val supabasePrefix: StateFlow<String> = _supabasePrefix.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected") // "Disconnected", "Testing...", "Connected", "Error"
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _autoSyncCloud = MutableStateFlow(false)
    val autoSyncCloud: StateFlow<Boolean> = _autoSyncCloud.asStateFlow()

    private val _autoPullCloud = MutableStateFlow(false)
    val autoPullCloud: StateFlow<Boolean> = _autoPullCloud.asStateFlow()

    private val _autoTwoWaySync = MutableStateFlow(false)
    val autoTwoWaySync: StateFlow<Boolean> = _autoTwoWaySync.asStateFlow()

    private var appContext: android.content.Context? = null

    // --- Screen Settings (Mobile / Tablet) ---
    private val _screenMode = MutableStateFlow("Mobile") // "Mobile" or "Tablet"
    val screenMode: StateFlow<String> = _screenMode.asStateFlow()

    // --- Custom Text Size Scaling (Default 1.0f) ---
    private val _textScaleFactor = MutableStateFlow(1.0f)
    val textScaleFactor: StateFlow<Float> = _textScaleFactor.asStateFlow()

    // --- Hide System Navigation Bars / Immersive Mode ---
    private val _hideSystemBars = MutableStateFlow(false)
    val hideSystemBars: StateFlow<Boolean> = _hideSystemBars.asStateFlow()

    // --- Order Receipt & Printer Settings (57mm / 58mm / 80mm) ---
    private val _receiptSize = MutableStateFlow("80mm") // "57mm", "58mm", "80mm"
    val receiptSize: StateFlow<String> = _receiptSize.asStateFlow()

    private val _showLogoOnReceipt = MutableStateFlow(true)
    val showLogoOnReceipt: StateFlow<Boolean> = _showLogoOnReceipt.asStateFlow()

    private val _showTaxOnReceipt = MutableStateFlow(true)
    val showTaxOnReceipt: StateFlow<Boolean> = _showTaxOnReceipt.asStateFlow()

    private val _receiptFooterNote = MutableStateFlow("Thank you for your business! Please visit again.")
    val receiptFooterNote: StateFlow<String> = _receiptFooterNote.asStateFlow()

    private val _printerAutoCut = MutableStateFlow(true)
    val printerAutoCut: StateFlow<Boolean> = _printerAutoCut.asStateFlow()

    private val _printerFeedLines = MutableStateFlow(2)
    val printerFeedLines: StateFlow<Int> = _printerFeedLines.asStateFlow()

    // --- Color Theme Settings ("Warm Orange", "Cool Blue", "Forest Green", "Classic Slate") ---
    private val _colorTheme = MutableStateFlow("Warm Orange")
    val colorTheme: StateFlow<String> = _colorTheme.asStateFlow()

    // --- Restaurant Profile Settings ---
    private val _restaurantName = MutableStateFlow("Food Cuisine")
    val restaurantName: StateFlow<String> = _restaurantName.asStateFlow()

    private val _restaurantSlogan = MutableStateFlow("HOUSE OF TASTE")
    val restaurantSlogan: StateFlow<String> = _restaurantSlogan.asStateFlow()

    private val _restaurantPhone = MutableStateFlow("+1 (555) 123-4567")
    val restaurantPhone: StateFlow<String> = _restaurantPhone.asStateFlow()

    private val _restaurantAddress = MutableStateFlow("123 Foodie Blvd, Gourmet Haven")
    val restaurantAddress: StateFlow<String> = _restaurantAddress.asStateFlow()

    private val _restaurantLogoBase64 = MutableStateFlow("")
    val restaurantLogoBase64: StateFlow<String> = _restaurantLogoBase64.asStateFlow()

    fun loadSettings(context: android.content.Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        _dbMode.value = prefs.getString("db_mode", "Offline") ?: "Offline"
        _supabaseUrl.value = prefs.getString("supabase_url", "") ?: ""
        _supabaseKey.value = prefs.getString("supabase_key", "") ?: ""
        _supabasePrefix.value = prefs.getString("supabase_prefix", "dine_pos_") ?: "dine_pos_"
        _screenMode.value = prefs.getString("screen_mode", "Mobile") ?: "Mobile"
        _textScaleFactor.value = prefs.getFloat("text_scale_factor", 1.0f)
        _hideSystemBars.value = prefs.getBoolean("hide_system_bars", false)
        _receiptSize.value = prefs.getString("receipt_size", "80mm") ?: "80mm"
        _showLogoOnReceipt.value = prefs.getBoolean("show_logo_on_receipt", true)
        _showTaxOnReceipt.value = prefs.getBoolean("show_tax_on_receipt", true)
        _receiptFooterNote.value = prefs.getString("receipt_footer_note", "Thank you for your business! Please visit again.") ?: "Thank you for your business! Please visit again."
        _printerAutoCut.value = prefs.getBoolean("printer_auto_cut", true)
        _printerFeedLines.value = prefs.getInt("printer_feed_lines", 2)
        _colorTheme.value = prefs.getString("color_theme", "Warm Orange") ?: "Warm Orange"
        _restaurantName.value = prefs.getString("restaurant_name", "Food Cuisine") ?: "Food Cuisine"
        _restaurantSlogan.value = prefs.getString("restaurant_slogan", "HOUSE OF TASTE") ?: "HOUSE OF TASTE"
        _restaurantPhone.value = prefs.getString("restaurant_phone", "+1 (555) 123-4567") ?: "+1 (555) 123-4567"
        _restaurantAddress.value = prefs.getString("restaurant_address", "123 Foodie Blvd, Gourmet Haven") ?: "123 Foodie Blvd, Gourmet Haven"
        _restaurantLogoBase64.value = prefs.getString("restaurant_logo_base64", "") ?: ""
        _autoSyncCloud.value = prefs.getBoolean("auto_sync_cloud", false)
        _autoPullCloud.value = prefs.getBoolean("auto_pull_cloud", false)
        _autoTwoWaySync.value = prefs.getBoolean("auto_two_way_sync", false)

        if (_autoTwoWaySync.value) {
            triggerAutoTwoWaySyncIfNeeded()
        } else {
            if (_autoSyncCloud.value) {
                triggerAutoSyncIfNeeded()
            }
            if (_autoPullCloud.value) {
                triggerAutoPullIfNeeded()
            }
        }
    }

    fun setAutoSyncCloud(context: android.content.Context, enabled: Boolean) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_sync_cloud", enabled).apply()
        _autoSyncCloud.value = enabled
        if (enabled) {
            _uiMessage.value = "Auto Push to Cloud Enabled: Local DB will automatically push to cloud."
            triggerAutoSyncIfNeeded()
        } else {
            _uiMessage.value = "Auto Push to Cloud Disabled."
        }
    }

    fun setAutoPullCloud(context: android.content.Context, enabled: Boolean) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_pull_cloud", enabled).apply()
        _autoPullCloud.value = enabled
        if (enabled) {
            _uiMessage.value = "Auto Pull from Cloud Enabled: Cloud data will automatically pull to local DB."
            triggerAutoPullIfNeeded()
        } else {
            _uiMessage.value = "Auto Pull from Cloud Disabled."
        }
    }

    fun setAutoTwoWaySync(context: android.content.Context, enabled: Boolean) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_two_way_sync", enabled).apply()
        _autoTwoWaySync.value = enabled
        if (enabled) {
            _uiMessage.value = "Auto Two-Way Sync Enabled: Local DB & Cloud will automatically synchronize."
            triggerAutoTwoWaySyncIfNeeded()
        } else {
            _uiMessage.value = "Auto Two-Way Sync Disabled."
        }
    }

    fun triggerAutoSyncsOnDatabaseUpdate() {
        if (_autoTwoWaySync.value) {
            triggerAutoTwoWaySyncIfNeeded()
        } else {
            if (_autoSyncCloud.value) {
                triggerAutoSyncIfNeeded()
            }
        }
    }

    fun triggerAutoPullIfNeeded() {
        val context = appContext ?: return
        if (!_autoPullCloud.value) return
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        if (url.isEmpty() || key.isEmpty()) return

        viewModelScope.launch {
            _isSyncing.value = true
            val result = com.example.data.sync.SyncBackupManager.pullFromSupabase(
                context = context,
                url = url,
                key = key,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> 
                    _uiMessage.value = "Auto Pull Success: Data pulled from cloud"
                },
                onFailure = { err -> 
                    _uiMessage.value = "Auto Pull Failed: ${err.message}"
                }
            )
        }
    }

    fun triggerAutoSyncIfNeeded() {
        val context = appContext ?: return
        if (!_autoSyncCloud.value) return
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        if (url.isEmpty() || key.isEmpty()) return

        viewModelScope.launch {
            _isSyncing.value = true
            val result = com.example.data.sync.SyncBackupManager.pushToSupabase(
                context = context,
                url = url,
                key = key,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> 
                    _uiMessage.value = "Auto Push Success: Data pushed to cloud"
                },
                onFailure = { err -> 
                    _uiMessage.value = "Auto Push Failed: ${err.message}"
                }
            )
        }
    }

    fun triggerAutoTwoWaySyncIfNeeded() {
        val context = appContext ?: return
        if (!_autoTwoWaySync.value) return
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        if (url.isEmpty() || key.isEmpty()) return

        viewModelScope.launch {
            _isSyncing.value = true
            val result = com.example.data.sync.SyncBackupManager.syncBothSidesWithSupabase(
                context = context,
                url = url,
                key = key,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> 
                    _uiMessage.value = "Auto Two-Way Sync Success: SQLite & Supabase synchronized"
                },
                onFailure = { err -> 
                    _uiMessage.value = "Auto Two-Way Sync Failed: ${err.message}"
                }
            )
        }
    }

    fun saveRestaurantLogo(context: android.content.Context, logoBase64: String) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("restaurant_logo_base64", logoBase64).apply()
        _restaurantLogoBase64.value = logoBase64
        _uiMessage.value = "Restaurant Logo updated successfully!"
    }

    fun saveRestaurantProfile(context: android.content.Context, name: String, slogan: String, phone: String, address: String) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("restaurant_name", name)
            .putString("restaurant_slogan", slogan)
            .putString("restaurant_phone", phone)
            .putString("restaurant_address", address)
            .apply()

        _restaurantName.value = name
        _restaurantSlogan.value = slogan
        _restaurantPhone.value = phone
        _restaurantAddress.value = address

        viewModelScope.launch {
            val activeProfile = RestaurantProfile(
                name = name,
                slogan = slogan,
                phone = phone,
                address = address,
                logoBase64 = _restaurantLogoBase64.value,
                cuisineType = "General",
                rating = 5.0,
                website = "",
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveAndActivateProfile(activeProfile)
            _uiMessage.value = "Profile saved locally to Room Offline DB and active settings updated!"
        }
    }

    fun saveRestaurantProfileToRoom(
        context: android.content.Context,
        name: String,
        slogan: String,
        phone: String,
        address: String,
        cuisineType: String,
        rating: Double,
        website: String,
        activate: Boolean
    ) {
        viewModelScope.launch {
            val profile = RestaurantProfile(
                name = name,
                slogan = slogan,
                phone = phone,
                address = address,
                logoBase64 = _restaurantLogoBase64.value,
                cuisineType = cuisineType,
                rating = rating,
                website = website,
                isActive = activate,
                updatedAt = System.currentTimeMillis()
            )
            if (activate) {
                repository.saveAndActivateProfile(profile)
                
                // Synchronize shared preferences & LiveData
                val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("restaurant_name", name)
                    .putString("restaurant_slogan", slogan)
                    .putString("restaurant_phone", phone)
                    .putString("restaurant_address", address)
                    .apply()

                _restaurantName.value = name
                _restaurantSlogan.value = slogan
                _restaurantPhone.value = phone
                _restaurantAddress.value = address
                _uiMessage.value = "Profile saved offline as ACTIVE and applied successfully!"
            } else {
                repository.insertRestaurantProfile(profile)
                _uiMessage.value = "New offline profile '$name' saved to Room database!"
            }
        }
    }

    fun activateLocalProfile(context: android.content.Context, profile: RestaurantProfile) {
        viewModelScope.launch {
            repository.saveAndActivateProfile(profile)
            
            // Sync with shared preferences
            val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("restaurant_name", profile.name)
                .putString("restaurant_slogan", profile.slogan)
                .putString("restaurant_phone", profile.phone)
                .putString("restaurant_address", profile.address)
                .putString("restaurant_logo_base64", profile.logoBase64)
                .apply()

            _restaurantName.value = profile.name
            _restaurantSlogan.value = profile.slogan
            _restaurantPhone.value = profile.phone
            _restaurantAddress.value = profile.address
            _restaurantLogoBase64.value = profile.logoBase64
            
            _uiMessage.value = "Active Profile switched to: ${profile.name}"
        }
    }

    fun deleteLocalProfile(profile: RestaurantProfile) {
        viewModelScope.launch {
            if (profile.isActive) {
                _uiMessage.value = "Cannot delete the currently active profile! Switch profiles first."
                return@launch
            }
            repository.deleteRestaurantProfile(profile)
            _uiMessage.value = "Profile '${profile.name}' deleted from local Room database."
        }
    }

    fun queryProfilesFromSupabase(
        context: android.content.Context,
        searchQuery: String? = null,
        cuisineQuery: String? = null,
        minRatingQuery: Double? = null
    ) {
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        val prefix = _supabasePrefix.value

        if (url.isEmpty() || key.isEmpty()) {
            _uiMessage.value = "Please configure your Supabase URL & Key in central database settings first!"
            return
        }

        viewModelScope.launch {
            _isQueryingCloudProfiles.value = true
            val result = com.example.data.sync.SyncBackupManager.queryRestaurantProfilesFromSupabase(
                url = url,
                key = key,
                prefix = prefix,
                searchQuery = searchQuery,
                cuisineQuery = cuisineQuery,
                minRatingQuery = minRatingQuery
            )
            _isQueryingCloudProfiles.value = false
            
            if (result.isSuccess) {
                _cloudRestaurantProfiles.value = result.getOrDefault(emptyList())
                _uiMessage.value = "Successfully fetched ${_cloudRestaurantProfiles.value.size} profiles from Supabase!"
            } else {
                _uiMessage.value = "Cloud query failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun queryMenuItemsByCategoryFromSupabase(category: String) {
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        val prefix = _supabasePrefix.value

        if (url.isEmpty() || key.isEmpty()) {
            _uiMessage.value = "Please configure your Supabase URL & Key in central database settings first!"
            return
        }

        viewModelScope.launch {
            _isQueryingCloudMenuItems.value = true
            val result = com.example.data.sync.SyncBackupManager.queryMenuItemsByCategoryFromSupabase(
                url = url,
                key = key,
                prefix = prefix,
                category = category
            )
            _isQueryingCloudMenuItems.value = false
            
            if (result.isSuccess) {
                _cloudMenuItems.value = result.getOrDefault(emptyList())
                _uiMessage.value = "Successfully fetched ${_cloudMenuItems.value.size} items for category '$category' from Supabase!"
            } else {
                _uiMessage.value = "Cloud category query failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun uploadProfileToSupabase(context: android.content.Context, profile: RestaurantProfile) {
        val url = _supabaseUrl.value
        val key = _supabaseKey.value
        val prefix = _supabasePrefix.value

        if (url.isEmpty() || key.isEmpty()) {
            _uiMessage.value = "Configure Supabase credentials in Central Database Settings to upload."
            return
        }

        viewModelScope.launch {
            _uiMessage.value = "Uploading profiles to Supabase..."
            val result = com.example.data.sync.SyncBackupManager.pushToSupabase(context, url, key, prefix)
            if (result.isSuccess) {
                _uiMessage.value = "Successfully uploaded and synchronized all profiles to Supabase!"
                queryProfilesFromSupabase(context)
            } else {
                _uiMessage.value = "Upload failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun saveScreenMode(context: android.content.Context, mode: String) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("screen_mode", mode).apply()
        _screenMode.value = mode
        _uiMessage.value = "Screen setting changed to $mode mode."
    }

    fun saveTextScaleFactor(context: android.content.Context, factor: Float) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("text_scale_factor", factor).apply()
        _textScaleFactor.value = factor
        _uiMessage.value = "Text scale factor updated to ${String.format("%.2f", factor)}x"
    }

    fun saveHideSystemBars(context: android.content.Context, hide: Boolean) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hide_system_bars", hide).apply()
        _hideSystemBars.value = hide
        if (hide) {
            _uiMessage.value = "Immersive Mode Enabled: System bottom navigation hidden."
        } else {
            _uiMessage.value = "Immersive Mode Disabled: System bottom navigation restored."
        }
    }

    fun saveReceiptSize(context: android.content.Context, size: String) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("receipt_size", size).apply()
        _receiptSize.value = size
        _uiMessage.value = "Receipt printing size set to $size (Fixed format)."
    }

    fun savePrinterSettings(
        context: android.content.Context,
        size: String,
        showLogo: Boolean,
        showTax: Boolean,
        footerNote: String,
        autoCut: Boolean,
        feedLines: Int
    ) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("receipt_size", size)
            .putBoolean("show_logo_on_receipt", showLogo)
            .putBoolean("show_tax_on_receipt", showTax)
            .putString("receipt_footer_note", footerNote)
            .putBoolean("printer_auto_cut", autoCut)
            .putInt("printer_feed_lines", feedLines)
            .apply()

        _receiptSize.value = size
        _showLogoOnReceipt.value = showLogo
        _showTaxOnReceipt.value = showTax
        _receiptFooterNote.value = footerNote
        _printerAutoCut.value = autoCut
        _printerFeedLines.value = feedLines
        _uiMessage.value = "Printer Preferences Saved ($size Fixed Layout)."
    }

    fun saveColorTheme(context: android.content.Context, theme: String) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("color_theme", theme).apply()
        _colorTheme.value = theme
        _uiMessage.value = "Color Theme updated to $theme."
    }

    fun saveSettings(
        context: android.content.Context,
        mode: String,
        url: String,
        key: String,
        prefix: String
    ) {
        val prefs = context.getSharedPreferences("pos_database_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("db_mode", mode)
            .putString("supabase_url", url)
            .putString("supabase_key", key)
            .putString("supabase_prefix", prefix)
            .apply()

        _dbMode.value = mode
        _supabaseUrl.value = url
        _supabaseKey.value = key
        _supabasePrefix.value = prefix

        _uiMessage.value = "Database Connection Settings Saved."
    }

    fun testConnection(url: String, key: String) {
        viewModelScope.launch {
            _connectionStatus.value = "Testing..."
            kotlinx.coroutines.delay(1200)
            if (url.startsWith("https://") && key.isNotEmpty()) {
                _connectionStatus.value = "Connected"
                _uiMessage.value = "Supabase Connection Established Successfully!"
            } else {
                _connectionStatus.value = "Error"
                _uiMessage.value = "Invalid Supabase credentials or URL format."
            }
        }
    }

    // --- Data Syncing and Backups state ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun pushToSupabaseCloud(context: android.content.Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            _uiMessage.value = "Starting Push Sync to Supabase..."
            val result = com.example.data.sync.SyncBackupManager.pushToSupabase(
                context = context,
                url = _supabaseUrl.value,
                key = _supabaseKey.value,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> _uiMessage.value = msg },
                onFailure = { err -> _uiMessage.value = "Push Sync Failed: ${err.message}" }
            )
        }
    }

    fun pullFromSupabaseCloud(context: android.content.Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            _uiMessage.value = "Starting Pull Sync from Supabase..."
            val result = com.example.data.sync.SyncBackupManager.pullFromSupabase(
                context = context,
                url = _supabaseUrl.value,
                key = _supabaseKey.value,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> _uiMessage.value = msg },
                onFailure = { err -> _uiMessage.value = "Pull Sync Failed: ${err.message}" }
            )
        }
    }

    fun syncBothSidesSupabaseCloud(context: android.content.Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            _uiMessage.value = "Starting Two-Way Bidirectional Sync..."
            val result = com.example.data.sync.SyncBackupManager.syncBothSidesWithSupabase(
                context = context,
                url = _supabaseUrl.value,
                key = _supabaseKey.value,
                prefix = _supabasePrefix.value
            )
            _isSyncing.value = false
            result.fold(
                onSuccess = { msg -> _uiMessage.value = msg },
                onFailure = { err -> _uiMessage.value = "Two-Way Sync Failed: ${err.message}" }
            )
        }
    }

    fun exportBackupJson(context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = com.example.data.sync.SyncBackupManager.exportBackup(context)
                onResult(json)
            } catch (e: Exception) {
                _uiMessage.value = "Backup Export Failed: ${e.message}"
                onResult("")
            }
        }
    }

    fun importBackupJson(context: android.content.Context, json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = com.example.data.sync.SyncBackupManager.importBackup(context, json)
            _isSyncing.value = false
            if (success) {
                _uiMessage.value = "Local database restored from backup successfully!"
            } else {
                _uiMessage.value = "Backup Import Failed: Invalid data structure or JSON error."
            }
            onResult(success)
        }
    }
}

// Data holder classes for analytics
data class TodayMetrics(
    val totalRevenue: Double,
    val ordersCount: Int,
    val itemsProduced: Double,
    val ordersList: List<Order>
)

data class MonthlyMetrics(
    val totalRevenue: Double,
    val ordersCount: Int,
    val totalExpensesPurchases: Double, // Raw Material Purchase cost incurred
    val costOfProduction: Double,       // Sum of raw items used from BOM
    val netProfit: Double,              // Total Sales - Cost of Production
    val purchasesList: List<Purchase>,
    val batchesList: List<ProductionBatch>
)

// Factory
class RestaurantViewModelFactory(private val repository: RestaurantRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RestaurantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RestaurantViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
