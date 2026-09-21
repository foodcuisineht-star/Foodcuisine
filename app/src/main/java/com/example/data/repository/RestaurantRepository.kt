package com.example.data.repository

import com.example.data.dao.RestaurantDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RestaurantRepository(private val restaurantDao: RestaurantDao) {

    // --- Users Flows & Actions ---
    val allUsers: Flow<List<User>> = restaurantDao.getAllUsers()
    suspend fun insertUser(user: User) = withContext(Dispatchers.IO) {
        restaurantDao.insertUser(user)
    }
    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        restaurantDao.deleteUser(user)
    }

    // --- Raw Inventory Flows & Actions ---
    val allRawMaterials: Flow<List<RawMaterial>> = restaurantDao.getAllRawMaterials()
    suspend fun getRawMaterialById(id: Int): RawMaterial? = withContext(Dispatchers.IO) {
        restaurantDao.getRawMaterialById(id)
    }
    suspend fun insertRawMaterial(material: RawMaterial): Long = withContext(Dispatchers.IO) {
        restaurantDao.insertRawMaterial(material)
    }
    suspend fun updateRawMaterial(material: RawMaterial) = withContext(Dispatchers.IO) {
        restaurantDao.updateRawMaterial(material)
    }
    suspend fun deleteRawMaterial(material: RawMaterial) = withContext(Dispatchers.IO) {
        restaurantDao.deleteRawMaterial(material)
    }

    // --- Direct Seeding helpers ---
    suspend fun getUsersCount(): Int = withContext(Dispatchers.IO) {
        restaurantDao.getUsersCount()
    }

    suspend fun getRawMaterialsCount(): Int = withContext(Dispatchers.IO) {
        restaurantDao.getRawMaterialsCount()
    }

    suspend fun insertProductionBatch(batch: ProductionBatch) = withContext(Dispatchers.IO) {
        restaurantDao.insertProductionBatch(batch)
    }
    suspend fun insertOrder(order: Order): Long = withContext(Dispatchers.IO) {
        restaurantDao.insertOrder(order)
    }
    suspend fun insertOrderItem(item: OrderItem) = withContext(Dispatchers.IO) {
        restaurantDao.insertOrderItem(item)
    }

    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        restaurantDao.deleteAllOrders()
        restaurantDao.deleteAllOrderItems()
        restaurantDao.deleteAllProductionBatches()
        restaurantDao.deleteAllPurchases()
    }

    suspend fun resetAllDatabase() = withContext(Dispatchers.IO) {
        restaurantDao.deleteAllOrders()
        restaurantDao.deleteAllOrderItems()
        restaurantDao.deleteAllProductionBatches()
        restaurantDao.deleteAllPurchases()
        restaurantDao.deleteAllRawInventory()
        restaurantDao.deleteAllMenuItems()
        restaurantDao.deleteAllBomRecipes()
        restaurantDao.deleteAllUsers()
    }

    // --- Purchases Flows & Actions ---
    val allPurchases: Flow<List<Purchase>> = restaurantDao.getAllPurchases()

    /**
     * Records a purchase of raw material, adds it to the stock,
     * and recalculates the weighted average cost dynamically.
     */
    suspend fun recordPurchase(purchase: Purchase): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val material = restaurantDao.getRawMaterialById(purchase.rawItemId)
                ?: return@withContext Result.failure(Exception("Raw material not found"))

            val currentQuantity = material.quantity
            val currentAvgCost = material.avgCostPerUnit

            val purchasedQuantity = purchase.quantity
            val totalPurchaseCost = purchase.cost

            val newQuantity = currentQuantity + purchasedQuantity
            val newAvgCost = if (newQuantity > 0) {
                ((currentQuantity * currentAvgCost) + totalPurchaseCost) / newQuantity
            } else {
                totalPurchaseCost / purchasedQuantity
            }

            // Update raw material in database
            val updatedMaterial = material.copy(
                quantity = newQuantity,
                avgCostPerUnit = newAvgCost
            )
            restaurantDao.updateRawMaterial(updatedMaterial)

            // Insert purchase log
            val rawId = restaurantDao.insertPurchase(purchase)
            Result.success(rawId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Menu Items Flows & Actions ---
    val allMenuItems: Flow<List<MenuItem>> = restaurantDao.getAllMenuItems()
    suspend fun getMenuItemById(id: Int): MenuItem? = withContext(Dispatchers.IO) {
        restaurantDao.getMenuItemById(id)
    }
    suspend fun insertMenuItem(item: MenuItem): Long = withContext(Dispatchers.IO) {
        restaurantDao.insertMenuItem(item)
    }
    suspend fun updateMenuItem(item: MenuItem) = withContext(Dispatchers.IO) {
        restaurantDao.updateMenuItem(item)
    }
    suspend fun deleteMenuItem(item: MenuItem) = withContext(Dispatchers.IO) {
        restaurantDao.deleteMenuItem(item)
    }

    // --- BOM Recipes Flows & Actions ---
    val allBomRecipes: Flow<List<BomIngredient>> = restaurantDao.getAllBomRecipes()

    fun getBomIngredientsForMenuItem(menuItemId: Int): Flow<List<BomIngredient>> =
        restaurantDao.getBomIngredientsForMenuItem(menuItemId)

    suspend fun createOrUpdateRecipe(menuItemId: Int, ingredients: List<BomIngredient>, recipeYield: Double) = withContext(Dispatchers.IO) {
        // First delete existing recipe for this menu item
        restaurantDao.deleteBomForMenuItem(menuItemId)
        
        // Save new recipe ingredients
        for (ingredient in ingredients) {
            restaurantDao.insertBomIngredient(ingredient.copy(menuItemId = menuItemId))
        }

        // Update MenuItem BOM flag and recipe yield
        val menuItem = restaurantDao.getMenuItemById(menuItemId)
        if (menuItem != null) {
            restaurantDao.updateMenuItem(
                menuItem.copy(
                    hasBom = ingredients.isNotEmpty(),
                    recipeYield = recipeYield
                )
            )
        }
    }

    // --- Production Batches Flows & Actions ---
    val allProductionBatches: Flow<List<ProductionBatch>> = restaurantDao.getAllProductionBatches()

    /**
     * Executes production batch and recalculates stocks.
     * Deducts scaled raw materials, calculates exact production costs based
     * on average purchase prices, updates menu item's ready quantities, and logs details.
     */
    suspend fun runProduction(menuItemId: Int, producedQuantity: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val menuItem = restaurantDao.getMenuItemById(menuItemId)
                ?: return@withContext Result.failure(Exception("Menu item not found"))

            val ingredients = restaurantDao.getBomIngredientsForMenuItemSync(menuItemId)
            if (ingredients.isEmpty()) {
                return@withContext Result.failure(Exception("No active recipe found for '${menuItem.name}'"))
            }

            // Calculation scaling ratio (producedQuantity / yield defined in recipe)
            val recipeYieldSize = menuItem.recipeYield
            if (recipeYieldSize <= 0) {
                return@withContext Result.failure(Exception("Invalid recipe yield size configured in BOM"))
            }
            val ratio = producedQuantity / recipeYieldSize

            // 1. Verify all raw materials exist and have enough quantity
            val rawMaterialsToUpdate = mutableListOf<RawMaterial>()
            var totalProductionCost = 0.0

            for (ingredient in ingredients) {
                val rawMaterial = restaurantDao.getRawMaterialById(ingredient.rawItemId)
                    ?: return@withContext Result.failure(Exception("Raw ingredient '${ingredient.rawItemName}' not found"))

                val neededQuantity = ingredient.requiredQuantity * ratio
                if (rawMaterial.quantity < neededQuantity) {
                    val formattedNeeded = String.format("%.2f", neededQuantity)
                    val formattedAvail = String.format("%.2f", rawMaterial.quantity)
                    return@withContext Result.failure(
                        Exception("Insufficient stock of '${rawMaterial.name}': Needed $formattedNeeded ${rawMaterial.unit}, but only $formattedAvail available.")
                    )
                }

                val costForThisIngredient = neededQuantity * rawMaterial.avgCostPerUnit
                totalProductionCost += costForThisIngredient

                val updatedRawMaterial = rawMaterial.copy(
                    quantity = rawMaterial.quantity - neededQuantity
                )
                rawMaterialsToUpdate.add(updatedRawMaterial)
            }

            // 2. Perform database actions in sequence
            // A. Deduct Raw Materials
            for (rawMat in rawMaterialsToUpdate) {
                restaurantDao.updateRawMaterial(rawMat)
            }

            // B. Increment available menu item ready stock
            val updatedMenuItem = menuItem.copy(
                availableQuantity = menuItem.availableQuantity + producedQuantity
            )
            restaurantDao.updateMenuItem(updatedMenuItem)

            // C. Insert Production Batch log
            val batchLog = ProductionBatch(
                menuItemId = menuItemId,
                menuItemName = menuItem.name,
                producedQuantity = producedQuantity,
                totalCost = totalProductionCost,
                productionDate = System.currentTimeMillis()
            )
            restaurantDao.insertProductionBatch(batchLog)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a production batch and optionally restores deducted raw inventory and adjusts menu ready stock.
     */
    suspend fun deleteProductionBatch(batch: ProductionBatch, revertInventory: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (revertInventory) {
                val menuItem = restaurantDao.getMenuItemById(batch.menuItemId)
                val ingredients = restaurantDao.getBomIngredientsForMenuItemSync(batch.menuItemId)
                val yieldSize = if (menuItem != null && menuItem.recipeYield > 0) menuItem.recipeYield else 1.0
                val ratio = batch.producedQuantity / yieldSize

                // 1. Re-add raw materials back into warehouse stock
                for (ingredient in ingredients) {
                    val rawMaterial = restaurantDao.getRawMaterialById(ingredient.rawItemId)
                    if (rawMaterial != null) {
                        val restoreQty = ingredient.requiredQuantity * ratio
                        val updatedRaw = rawMaterial.copy(
                            quantity = rawMaterial.quantity + restoreQty
                        )
                        restaurantDao.updateRawMaterial(updatedRaw)
                    }
                }

                // 2. Deduct ready dish stock (do not go below 0)
                if (menuItem != null) {
                    val newReadyQty = maxOf(0.0, menuItem.availableQuantity - batch.producedQuantity)
                    restaurantDao.updateMenuItem(menuItem.copy(availableQuantity = newReadyQty))
                }
            }

            // 3. Delete batch record
            restaurantDao.deleteProductionBatch(batch)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing production batch and adjusts inventory differences.
     */
    suspend fun updateProductionBatch(
        updatedBatch: ProductionBatch,
        previousBatch: ProductionBatch,
        adjustInventory: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (adjustInventory && updatedBatch.producedQuantity != previousBatch.producedQuantity) {
                val qtyDelta = updatedBatch.producedQuantity - previousBatch.producedQuantity
                val menuItem = restaurantDao.getMenuItemById(updatedBatch.menuItemId)
                val ingredients = restaurantDao.getBomIngredientsForMenuItemSync(updatedBatch.menuItemId)
                val yieldSize = if (menuItem != null && menuItem.recipeYield > 0) menuItem.recipeYield else 1.0
                val ratioDelta = qtyDelta / yieldSize

                // If increasing production, check if enough raw material stock exists
                if (qtyDelta > 0) {
                    for (ingredient in ingredients) {
                        val rawMaterial = restaurantDao.getRawMaterialById(ingredient.rawItemId)
                            ?: return@withContext Result.failure(Exception("Raw ingredient '${ingredient.rawItemName}' not found"))
                        val needed = ingredient.requiredQuantity * ratioDelta
                        if (rawMaterial.quantity < needed) {
                            return@withContext Result.failure(
                                Exception("Insufficient stock of '${rawMaterial.name}': Needed additional ${String.format("%.2f", needed)} ${rawMaterial.unit}")
                            )
                        }
                    }
                }

                // Update raw materials
                var newTotalCost = 0.0
                for (ingredient in ingredients) {
                    val rawMaterial = restaurantDao.getRawMaterialById(ingredient.rawItemId)
                    if (rawMaterial != null) {
                        val rawDelta = ingredient.requiredQuantity * ratioDelta
                        val updatedRaw = rawMaterial.copy(
                            quantity = maxOf(0.0, rawMaterial.quantity - rawDelta)
                        )
                        restaurantDao.updateRawMaterial(updatedRaw)
                        newTotalCost += (ingredient.requiredQuantity * (updatedBatch.producedQuantity / yieldSize)) * rawMaterial.avgCostPerUnit
                    }
                }

                // Update menu item ready stock
                if (menuItem != null) {
                    val updatedReadyStock = maxOf(0.0, menuItem.availableQuantity + qtyDelta)
                    restaurantDao.updateMenuItem(menuItem.copy(availableQuantity = updatedReadyStock))
                }

                restaurantDao.updateProductionBatch(
                    updatedBatch.copy(totalCost = if (newTotalCost > 0) newTotalCost else updatedBatch.totalCost)
                )
            } else {
                restaurantDao.updateProductionBatch(updatedBatch)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Orders Flows & Actions ---
    val allOrders: Flow<List<Order>> = restaurantDao.getAllOrders()

    fun getOrderItemsForOrder(orderId: Int): Flow<List<OrderItem>> =
        restaurantDao.getOrderItemsForOrder(orderId)

    /**
     * Creates an order with items, reduces inventory of Menu Items,
     * calculates taxes automatically, and persists historical data.
     */
    suspend fun createOrder(order: Order, items: List<OrderItem>): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // 1. Verify and deduct menu items ready quantities
            val menuItemsToUpdate = mutableListOf<MenuItem>()

            for (item in items) {
                val menuItem = restaurantDao.getMenuItemById(item.menuItemId)
                    ?: return@withContext Result.failure(Exception("Menu item '${item.menuItemName}' not found"))

                val updatedMenuItem = menuItem.copy(
                    availableQuantity = menuItem.availableQuantity - item.quantity
                )
                menuItemsToUpdate.add(updatedMenuItem)
            }

            // 2. Insert Order
            val orderId = restaurantDao.insertOrder(order)

            // 3. Update Menu Items Stock & Insert Order Items
            for (i in items.indices) {
                val originalItem = items[i]
                val updatedMenuItem = menuItemsToUpdate[i]
                
                restaurantDao.updateMenuItem(updatedMenuItem)
                restaurantDao.insertOrderItem(originalItem.copy(orderId = orderId.toInt()))
            }

            Result.success(orderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an order's status. If transitioned to "Cancelled", restores the
     * menu item quantities appropriately to avoid stock errors.
     */
    suspend fun updateOrderStatus(orderId: Int, nextStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val order = restaurantDao.getOrderById(orderId)
                ?: return@withContext Result.failure(Exception("Order not found"))

            if (order.status == nextStatus) {
                return@withContext Result.success(Unit) // no status change
            }

            val originalStatus = order.status

            // If moving to Cancelled, refund menu items ready stock
            if (nextStatus == "Cancelled" && originalStatus != "Cancelled") {
                val orderItems = restaurantDao.getOrderItemsForOrderSync(orderId)
                for (item in orderItems) {
                    val menuItem = restaurantDao.getMenuItemById(item.menuItemId)
                    if (menuItem != null) {
                        restaurantDao.updateMenuItem(
                            menuItem.copy(
                                availableQuantity = menuItem.availableQuantity + item.quantity
                            )
                        )
                    }
                }
            }
            
            // If moving AWAY from Cancelled (e.g. restoring a cancelled order?),
            // we should deduct. But in practice, cancelling is permanent in restaurants,
            // so we just refund. Just in case, let's also support status updates.
            val updatedOrder = order.copy(status = nextStatus)
            restaurantDao.updateOrder(updatedOrder)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderPaymentStatus(orderId: Int, nextPaymentStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val order = restaurantDao.getOrderById(orderId)
                ?: return@withContext Result.failure(Exception("Order not found"))
            val updatedOrder = order.copy(paymentStatus = nextPaymentStatus)
            restaurantDao.updateOrder(updatedOrder)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Restaurant Profile Methods ---
    val allRestaurantProfiles: Flow<List<RestaurantProfile>> = restaurantDao.getAllRestaurantProfiles()

    suspend fun getRestaurantProfileById(id: Int): RestaurantProfile? = withContext(Dispatchers.IO) {
        restaurantDao.getRestaurantProfileById(id)
    }

    suspend fun getActiveRestaurantProfile(): RestaurantProfile? = withContext(Dispatchers.IO) {
        restaurantDao.getActiveRestaurantProfile()
    }

    suspend fun insertRestaurantProfile(profile: RestaurantProfile): Long = withContext(Dispatchers.IO) {
        restaurantDao.insertRestaurantProfile(profile)
    }

    suspend fun updateRestaurantProfile(profile: RestaurantProfile) = withContext(Dispatchers.IO) {
        restaurantDao.updateRestaurantProfile(profile)
    }

    suspend fun deleteRestaurantProfile(profile: RestaurantProfile) = withContext(Dispatchers.IO) {
        restaurantDao.deleteRestaurantProfile(profile)
    }

    suspend fun saveAndActivateProfile(profile: RestaurantProfile): Long = withContext(Dispatchers.IO) {
        restaurantDao.deactivateAllProfiles()
        val profileWithActive = profile.copy(isActive = true, updatedAt = System.currentTimeMillis())
        restaurantDao.insertRestaurantProfile(profileWithActive)
    }

    suspend fun updateRiderDetails(
        orderId: Int,
        riderName: String,
        riderPhone: String,
        riderBikeNumber: String,
        riderCharges: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val order = restaurantDao.getOrderById(orderId)
                ?: return@withContext Result.failure(Exception("Order not found"))
            val updatedOrder = order.copy(
                riderName = riderName,
                riderPhone = riderPhone,
                riderBikeNumber = riderBikeNumber,
                riderCharges = riderCharges
            )
            restaurantDao.updateOrder(updatedOrder)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllRestaurantProfiles() = withContext(Dispatchers.IO) {
        restaurantDao.deleteAllRestaurantProfiles()
    }
}
