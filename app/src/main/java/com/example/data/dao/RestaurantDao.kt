package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {

    // --- Users ---
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    // --- Raw Inventory ---
    @Query("SELECT * FROM raw_inventory ORDER BY name ASC")
    fun getAllRawMaterials(): Flow<List<RawMaterial>>

    @Query("SELECT * FROM raw_inventory WHERE id = :id")
    suspend fun getRawMaterialById(id: Int): RawMaterial?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawMaterial(material: RawMaterial): Long

    @Update
    suspend fun updateRawMaterial(material: RawMaterial)

    @Delete
    suspend fun deleteRawMaterial(material: RawMaterial)

    // --- Purchases ---
    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    // --- Menu Items ---
    @Query("SELECT * FROM menu_items ORDER BY name ASC")
    fun getAllMenuItems(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getMenuItemById(id: Int): MenuItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItem(item: MenuItem): Long

    @Update
    suspend fun updateMenuItem(item: MenuItem)

    @Delete
    suspend fun deleteMenuItem(item: MenuItem)

    // --- BOM Recipes ---
    @Query("SELECT * FROM bom_recipes")
    fun getAllBomRecipes(): Flow<List<BomIngredient>>

    @Query("SELECT * FROM bom_recipes WHERE menuItemId = :menuItemId")
    fun getBomIngredientsForMenuItem(menuItemId: Int): Flow<List<BomIngredient>>

    @Query("SELECT * FROM bom_recipes WHERE menuItemId = :menuItemId")
    suspend fun getBomIngredientsForMenuItemSync(menuItemId: Int): List<BomIngredient>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBomIngredient(ingredient: BomIngredient): Long

    @Query("DELETE FROM bom_recipes WHERE menuItemId = :menuItemId")
    suspend fun deleteBomForMenuItem(menuItemId: Int)

    // --- Production Batches ---
    @Query("SELECT * FROM production_batches ORDER BY productionDate DESC")
    fun getAllProductionBatches(): Flow<List<ProductionBatch>>

    @Query("SELECT * FROM production_batches WHERE id = :id")
    suspend fun getProductionBatchById(id: Int): ProductionBatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductionBatch(batch: ProductionBatch): Long

    @Update
    suspend fun updateProductionBatch(batch: ProductionBatch)

    @Delete
    suspend fun deleteProductionBatch(batch: ProductionBatch)

    @Query("DELETE FROM production_batches WHERE id = :id")
    suspend fun deleteProductionBatchById(id: Int)

    // --- Orders ---
    @Query("SELECT * FROM orders ORDER BY orderDate DESC")
    fun getAllOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Int): Order?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    // --- Order Items ---
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getOrderItemsForOrder(orderId: Int): Flow<List<OrderItem>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItemsForOrderSync(orderId: Int): List<OrderItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItem): Long

    // --- Reset & Clear Database ---
    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun deleteAllOrderItems()

    @Query("DELETE FROM production_batches")
    suspend fun deleteAllProductionBatches()

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()

    @Query("DELETE FROM raw_inventory")
    suspend fun deleteAllRawInventory()

    @Query("DELETE FROM menu_items")
    suspend fun deleteAllMenuItems()

    @Query("DELETE FROM bom_recipes")
    suspend fun deleteAllBomRecipes()

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUsersCount(): Int

    @Query("SELECT COUNT(*) FROM raw_inventory")
    suspend fun getRawMaterialsCount(): Int

    // --- Restaurant Profiles ---
    @Query("SELECT * FROM restaurant_profiles ORDER BY updatedAt DESC")
    fun getAllRestaurantProfiles(): Flow<List<RestaurantProfile>>

    @Query("SELECT * FROM restaurant_profiles WHERE id = :id")
    suspend fun getRestaurantProfileById(id: Int): RestaurantProfile?

    @Query("SELECT * FROM restaurant_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRestaurantProfile(): RestaurantProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestaurantProfile(profile: RestaurantProfile): Long

    @Update
    suspend fun updateRestaurantProfile(profile: RestaurantProfile)

    @Delete
    suspend fun deleteRestaurantProfile(profile: RestaurantProfile)

    @Query("UPDATE restaurant_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    @Query("DELETE FROM restaurant_profiles")
    suspend fun deleteAllRestaurantProfiles()
}
