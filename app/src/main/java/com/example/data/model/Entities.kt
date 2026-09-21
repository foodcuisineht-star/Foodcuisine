package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val role: String, // "Admin" or "Cashier"
    val password: String = "1234"
)

@Entity(tableName = "raw_inventory")
data class RawMaterial(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double, // in standard unit
    val unit: String,     // "KG", "g", "L", "ml", "pcs", etc.
    val avgCostPerUnit: Double, // calculated on purchases
    val lowStockThreshold: Double // threshold alarm
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rawItemId: Int,
    val rawItemName: String,
    val quantity: Double,
    val cost: Double, // Total Cost
    val purchaseDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val price: Double,
    val taxPercent: Double, // e.g. 10.0 for 10%
    val availableQuantity: Double = 0.0, // produced via production batch
    val recipeYield: Double = 12.0, // Quantity produced by this recipe, e.g. 12.0 patties
    val hasBom: Boolean = false,
    val category: String = "General",
    val imageBase64: String = ""
)

@Entity(tableName = "bom_recipes")
data class BomIngredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val menuItemId: Int, // foreign key relation
    val rawItemId: Int,
    val rawItemName: String,
    val requiredQuantity: Double, // amount needed for the recipeYield of MenuItem
    val unit: String // ingredient unit
)

@Entity(tableName = "production_batches")
data class ProductionBatch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val menuItemId: Int,
    val menuItemName: String,
    val producedQuantity: Double, // batch output size (e.g. 12, 24, etc.)
    val totalCost: Double, // sum of raw materials cost during actual production
    val productionDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderNumber: String, // ORD-001, etc.
    val type: String, // "Dine-In" or "Takeaway"
    val status: String, // "Pending", "Preparing", "Ready", "Completed", "Cancelled"
    val totalItemsQuantity: Double,
    val subtotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val orderDate: Long = System.currentTimeMillis(),
    val customerName: String = "",
    val customerPhone: String = "",
    val paymentMethod: String = "Cash", // Cash, Card, UPI, Credit / On Account, Scheduled Payment
    val creditDueDate: Long? = null,
    val paymentStatus: String = "Paid", // Paid, Unpaid (Credit), Scheduled
    val customerAddress: String = "",
    val riderName: String = "",
    val riderPhone: String = "",
    val riderBikeNumber: String = "",
    val riderCharges: Double = 0.0
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderId: Int,
    val menuItemId: Int,
    val menuItemName: String,
    val quantity: Double,
    val unitPrice: Double,
    val taxPercent: Double,
    val taxAmount: Double,
    val totalAmount: Double
)

@Entity(tableName = "restaurant_profiles")
data class RestaurantProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val slogan: String,
    val phone: String,
    val address: String,
    val logoBase64: String = "",
    val cuisineType: String = "General",
    val rating: Double = 5.0,
    val website: String = "",
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
