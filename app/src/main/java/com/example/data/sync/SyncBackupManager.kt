package com.example.data.sync

import android.content.Context
import com.example.data.db.RestaurantDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SyncBackupManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    // ==========================================
    // BACKUP & RESTORE METHODS
    // ==========================================

    /**
     * Exports all database tables to a unified JSON string representation.
     */
    suspend fun exportBackup(context: Context): String = withContext(Dispatchers.IO) {
        val db = RestaurantDatabase.getDatabase(context)
        val dao = db.restaurantDao()

        val backupObj = JSONObject()

        // 1. Users
        val users = dao.getAllUsers().first()
        val usersArray = JSONArray()
        for (u in users) {
            usersArray.put(JSONObject().apply {
                put("id", u.id)
                put("username", u.username)
                put("role", u.role)
                put("password", u.password)
            })
        }
        backupObj.put("users", usersArray)

        // 2. Raw Materials
        val materials = dao.getAllRawMaterials().first()
        val materialsArray = JSONArray()
        for (m in materials) {
            materialsArray.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("quantity", m.quantity)
                put("unit", m.unit)
                put("avgCostPerUnit", m.avgCostPerUnit)
                put("lowStockThreshold", m.lowStockThreshold)
            })
        }
        backupObj.put("raw_inventory", materialsArray)

        // 3. Purchases
        val purchases = dao.getAllPurchases().first()
        val purchasesArray = JSONArray()
        for (p in purchases) {
            purchasesArray.put(JSONObject().apply {
                put("id", p.id)
                put("rawItemId", p.rawItemId)
                put("rawItemName", p.rawItemName)
                put("quantity", p.quantity)
                put("cost", p.cost)
                put("purchaseDate", p.purchaseDate)
            })
        }
        backupObj.put("purchases", purchasesArray)

        // 4. Menu Items
        val menuItems = dao.getAllMenuItems().first()
        val menuItemsArray = JSONArray()
        for (mi in menuItems) {
            menuItemsArray.put(JSONObject().apply {
                put("id", mi.id)
                put("name", mi.name)
                put("price", mi.price)
                put("taxPercent", mi.taxPercent)
                put("availableQuantity", mi.availableQuantity)
                put("recipeYield", mi.recipeYield)
                put("hasBom", mi.hasBom)
                put("category", mi.category)
                put("imageBase64", mi.imageBase64)
            })
        }
        backupObj.put("menu_items", menuItemsArray)

        // 5. BOM Recipes
        val bomList = mutableListOf<BomIngredient>()
        for (mi in menuItems) {
            val recipe = dao.getBomIngredientsForMenuItem(mi.id).first()
            bomList.addAll(recipe)
        }
        val bomArray = JSONArray()
        for (b in bomList) {
            bomArray.put(JSONObject().apply {
                put("id", b.id)
                put("menuItemId", b.menuItemId)
                put("rawItemId", b.rawItemId)
                put("rawItemName", b.rawItemName)
                put("requiredQuantity", b.requiredQuantity)
                put("unit", b.unit)
            })
        }
        backupObj.put("bom_recipes", bomArray)

        // 6. Production Batches
        val batches = dao.getAllProductionBatches().first()
        val batchesArray = JSONArray()
        for (pb in batches) {
            batchesArray.put(JSONObject().apply {
                put("id", pb.id)
                put("menuItemId", pb.menuItemId)
                put("menuItemName", pb.menuItemName)
                put("producedQuantity", pb.producedQuantity)
                put("totalCost", pb.totalCost)
                put("productionDate", pb.productionDate)
            })
        }
        backupObj.put("production_batches", batchesArray)

        // 7. Orders
        val orders = dao.getAllOrders().first()
        val ordersArray = JSONArray()
        for (o in orders) {
            ordersArray.put(JSONObject().apply {
                put("id", o.id)
                put("orderNumber", o.orderNumber)
                put("type", o.type)
                put("status", o.status)
                put("totalItemsQuantity", o.totalItemsQuantity)
                put("subtotal", o.subtotal)
                put("taxAmount", o.taxAmount)
                put("totalAmount", o.totalAmount)
                put("orderDate", o.orderDate)
                put("customerName", o.customerName)
                put("customerPhone", o.customerPhone)
                put("paymentMethod", o.paymentMethod)
                put("creditDueDate", o.creditDueDate ?: JSONObject.NULL)
                put("paymentStatus", o.paymentStatus)
                put("customerAddress", o.customerAddress)
                put("riderName", o.riderName)
                put("riderPhone", o.riderPhone)
                put("riderBikeNumber", o.riderBikeNumber)
                put("riderCharges", o.riderCharges)
            })
        }
        backupObj.put("orders", ordersArray)

        // 8. Order Items
        val orderItemsList = mutableListOf<OrderItem>()
        for (o in orders) {
            val items = dao.getOrderItemsForOrder(o.id).first()
            orderItemsList.addAll(items)
        }
        val orderItemsArray = JSONArray()
        for (oi in orderItemsList) {
            orderItemsArray.put(JSONObject().apply {
                put("id", oi.id)
                put("orderId", oi.orderId)
                put("menuItemId", oi.menuItemId)
                put("menuItemName", oi.menuItemName)
                put("quantity", oi.quantity)
                put("unitPrice", oi.unitPrice)
                put("taxPercent", oi.taxPercent)
                put("taxAmount", oi.taxAmount)
                put("totalAmount", oi.totalAmount)
            })
        }
        backupObj.put("order_items", orderItemsArray)

        // 9. Restaurant Profiles
        val profiles = dao.getAllRestaurantProfiles().first()
        val profilesArray = JSONArray()
        for (p in profiles) {
            profilesArray.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("slogan", p.slogan)
                put("phone", p.phone)
                put("address", p.address)
                put("logoBase64", p.logoBase64)
                put("cuisineType", p.cuisineType)
                put("rating", p.rating)
                put("website", p.website)
                put("isActive", p.isActive)
                put("updatedAt", p.updatedAt)
            })
        }
        backupObj.put("restaurant_profiles", profilesArray)

        backupObj.toString(2)
    }

    /**
     * Clears all local database tables and restores them from a backup JSON string.
     */
    suspend fun importBackup(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = RestaurantDatabase.getDatabase(context)
            val dao = db.restaurantDao()
            val backupObj = JSONObject(jsonString)

            // Perform in a safe transaction
            db.runInTransaction {
                // Clear tables sequentially
                kotlinx.coroutines.runBlocking {
                    dao.deleteAllOrders()
                    dao.deleteAllOrderItems()
                    dao.deleteAllProductionBatches()
                    dao.deleteAllPurchases()
                    dao.deleteAllRawInventory()
                    dao.deleteAllMenuItems()
                    dao.deleteAllBomRecipes()
                    dao.deleteAllUsers()

                    // Restore Users
                    if (backupObj.has("users")) {
                        val arr = backupObj.getJSONArray("users")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertUser(User(
                                id = obj.optInt("id", 0),
                                username = obj.getString("username"),
                                role = obj.getString("role"),
                                password = obj.optString("password", "1234")
                            ))
                        }
                    }

                    // Restore Raw Materials
                    if (backupObj.has("raw_inventory")) {
                        val arr = backupObj.getJSONArray("raw_inventory")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertRawMaterial(RawMaterial(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                quantity = obj.getDouble("quantity"),
                                unit = obj.getString("unit"),
                                avgCostPerUnit = obj.optDouble("avgCostPerUnit", 0.0),
                                lowStockThreshold = obj.optDouble("lowStockThreshold", 0.0)
                            ))
                        }
                    }

                    // Restore Purchases
                    if (backupObj.has("purchases")) {
                        val arr = backupObj.getJSONArray("purchases")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertPurchase(Purchase(
                                id = obj.optInt("id", 0),
                                rawItemId = obj.getInt("rawItemId"),
                                rawItemName = obj.getString("rawItemName"),
                                quantity = obj.getDouble("quantity"),
                                cost = obj.getDouble("cost"),
                                purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis())
                            ))
                        }
                    }

                    // Restore Menu Items
                    if (backupObj.has("menu_items")) {
                        val arr = backupObj.getJSONArray("menu_items")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertMenuItem(MenuItem(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                price = obj.getDouble("price"),
                                taxPercent = obj.optDouble("taxPercent", 0.0),
                                availableQuantity = obj.optDouble("availableQuantity", 0.0),
                                recipeYield = obj.optDouble("recipeYield", 12.0),
                                hasBom = obj.optBoolean("hasBom", false),
                                category = obj.optString("category", "General"),
                                imageBase64 = obj.optString("imageBase64", "")
                            ))
                        }
                    }

                    // Restore BOM Recipes
                    if (backupObj.has("bom_recipes")) {
                        val arr = backupObj.getJSONArray("bom_recipes")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertBomIngredient(BomIngredient(
                                id = obj.optInt("id", 0),
                                menuItemId = obj.getInt("menuItemId"),
                                rawItemId = obj.getInt("rawItemId"),
                                rawItemName = obj.getString("rawItemName"),
                                requiredQuantity = obj.getDouble("requiredQuantity"),
                                unit = obj.getString("unit")
                            ))
                        }
                    }

                    // Restore Production Batches
                    if (backupObj.has("production_batches")) {
                        val arr = backupObj.getJSONArray("production_batches")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertProductionBatch(ProductionBatch(
                                id = obj.optInt("id", 0),
                                menuItemId = obj.getInt("menuItemId"),
                                menuItemName = obj.getString("menuItemName"),
                                producedQuantity = obj.getDouble("producedQuantity"),
                                totalCost = obj.getDouble("totalCost"),
                                productionDate = obj.optLong("productionDate", System.currentTimeMillis())
                            ))
                        }
                    }

                    // Restore Orders
                    if (backupObj.has("orders")) {
                        val arr = backupObj.getJSONArray("orders")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val creditVal = if (obj.isNull("creditDueDate")) null else obj.getLong("creditDueDate")
                            dao.insertOrder(Order(
                                id = obj.optInt("id", 0),
                                orderNumber = obj.getString("orderNumber"),
                                type = obj.getString("type"),
                                status = obj.getString("status"),
                                totalItemsQuantity = obj.getDouble("totalItemsQuantity"),
                                subtotal = obj.getDouble("subtotal"),
                                taxAmount = obj.getDouble("taxAmount"),
                                totalAmount = obj.getDouble("totalAmount"),
                                orderDate = obj.optLong("orderDate", System.currentTimeMillis()),
                                customerName = obj.optString("customerName", ""),
                                customerPhone = obj.optString("customerPhone", ""),
                                paymentMethod = obj.optString("paymentMethod", "Cash"),
                                creditDueDate = creditVal,
                                paymentStatus = obj.optString("paymentStatus", "Paid"),
                                customerAddress = obj.optString("customerAddress", ""),
                                riderName = obj.optString("riderName", ""),
                                riderPhone = obj.optString("riderPhone", ""),
                                riderBikeNumber = obj.optString("riderBikeNumber", ""),
                                riderCharges = obj.optDouble("riderCharges", 0.0)
                            ))
                        }
                    }

                    // Restore Order Items
                    if (backupObj.has("order_items")) {
                        val arr = backupObj.getJSONArray("order_items")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertOrderItem(OrderItem(
                                id = obj.optInt("id", 0),
                                orderId = obj.getInt("orderId"),
                                menuItemId = obj.getInt("menuItemId"),
                                menuItemName = obj.getString("menuItemName"),
                                quantity = obj.getDouble("quantity"),
                                unitPrice = obj.getDouble("unitPrice"),
                                taxPercent = obj.optDouble("taxPercent", 0.0),
                                taxAmount = obj.optDouble("taxAmount", 0.0),
                                totalAmount = obj.getDouble("totalAmount")
                            ))
                        }
                    }

                    // Restore Restaurant Profiles
                    if (backupObj.has("restaurant_profiles")) {
                        val arr = backupObj.getJSONArray("restaurant_profiles")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            dao.insertRestaurantProfile(RestaurantProfile(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                slogan = obj.optString("slogan", ""),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                logoBase64 = obj.optString("logoBase64", ""),
                                cuisineType = obj.optString("cuisineType", "General"),
                                rating = obj.optDouble("rating", 5.0),
                                website = obj.optString("website", ""),
                                isActive = obj.optBoolean("isActive", false),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            ))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==========================================
    // SUPABASE DATA SYNC (PUSH / PULL)
    // ==========================================

    /**
     * Pushes all local SQLite table rows to their respective Supabase tables.
     * Uses Upsert (Prefer: resolution=merge-duplicates) based on primary key 'id'.
     */
    suspend fun pushToSupabase(
        context: Context,
        url: String,
        key: String,
        prefix: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are not configured under Cloud settings."))
        }

        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val db = RestaurantDatabase.getDatabase(context)
        val dao = db.restaurantDao()

        try {
            // Prepare payload for all 8 tables
            val tablesToPush = mapOf(
                "users" to JSONArray().apply {
                    for (u in dao.getAllUsers().first()) {
                        put(JSONObject().apply {
                            put("id", u.id)
                            put("username", u.username)
                            put("role", u.role)
                            put("password", u.password)
                        })
                    }
                },
                "raw_inventory" to JSONArray().apply {
                    for (m in dao.getAllRawMaterials().first()) {
                        put(JSONObject().apply {
                            put("id", m.id)
                            put("name", m.name)
                            put("quantity", m.quantity)
                            put("unit", m.unit)
                            put("avgCostPerUnit", m.avgCostPerUnit)
                            put("lowStockThreshold", m.lowStockThreshold)
                        })
                    }
                },
                "purchases" to JSONArray().apply {
                    for (p in dao.getAllPurchases().first()) {
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("rawItemId", p.rawItemId)
                            put("rawItemName", p.rawItemName)
                            put("quantity", p.quantity)
                            put("cost", p.cost)
                            put("purchaseDate", p.purchaseDate)
                        })
                    }
                },
                "menu_items" to JSONArray().apply {
                    for (mi in dao.getAllMenuItems().first()) {
                        put(JSONObject().apply {
                            put("id", mi.id)
                            put("name", mi.name)
                            put("price", mi.price)
                            put("taxPercent", mi.taxPercent)
                            put("availableQuantity", mi.availableQuantity)
                            put("recipeYield", mi.recipeYield)
                            put("hasBom", mi.hasBom)
                            put("category", mi.category)
                            put("imageBase64", mi.imageBase64)
                        })
                    }
                },
                "bom_recipes" to JSONArray().apply {
                    val menuItems = dao.getAllMenuItems().first()
                    for (mi in menuItems) {
                        for (b in dao.getBomIngredientsForMenuItem(mi.id).first()) {
                            put(JSONObject().apply {
                                put("id", b.id)
                                put("menuItemId", b.menuItemId)
                                put("rawItemId", b.rawItemId)
                                put("rawItemName", b.rawItemName)
                                put("requiredQuantity", b.requiredQuantity)
                                put("unit", b.unit)
                            })
                        }
                    }
                },
                "production_batches" to JSONArray().apply {
                    for (pb in dao.getAllProductionBatches().first()) {
                        put(JSONObject().apply {
                            put("id", pb.id)
                            put("menuItemId", pb.menuItemId)
                            put("menuItemName", pb.menuItemName)
                            put("producedQuantity", pb.producedQuantity)
                            put("totalCost", pb.totalCost)
                            put("productionDate", pb.productionDate)
                        })
                    }
                },
                "orders" to JSONArray().apply {
                    for (o in dao.getAllOrders().first()) {
                        put(JSONObject().apply {
                            put("id", o.id)
                            put("orderNumber", o.orderNumber)
                            put("type", o.type)
                            put("status", o.status)
                            put("totalItemsQuantity", o.totalItemsQuantity)
                            put("subtotal", o.subtotal)
                            put("taxAmount", o.taxAmount)
                            put("totalAmount", o.totalAmount)
                            put("orderDate", o.orderDate)
                            put("customerName", o.customerName)
                            put("customerPhone", o.customerPhone)
                            put("paymentMethod", o.paymentMethod)
                            put("creditDueDate", o.creditDueDate ?: JSONObject.NULL)
                            put("paymentStatus", o.paymentStatus)
                            put("customerAddress", o.customerAddress)
                            put("riderName", o.riderName)
                            put("riderPhone", o.riderPhone)
                            put("riderBikeNumber", o.riderBikeNumber)
                            put("riderCharges", o.riderCharges)
                        })
                    }
                },
                "order_items" to JSONArray().apply {
                    val orders = dao.getAllOrders().first()
                    for (o in orders) {
                        for (oi in dao.getOrderItemsForOrder(o.id).first()) {
                            put(JSONObject().apply {
                                put("id", oi.id)
                                put("orderId", oi.orderId)
                                put("menuItemId", oi.menuItemId)
                                put("menuItemName", oi.menuItemName)
                                put("quantity", oi.quantity)
                                put("unitPrice", oi.unitPrice)
                                put("taxPercent", oi.taxPercent)
                                put("taxAmount", oi.taxAmount)
                                put("totalAmount", oi.totalAmount)
                            })
                        }
                    }
                },
                "restaurant_profiles" to JSONArray().apply {
                    for (rp in dao.getAllRestaurantProfiles().first()) {
                        put(JSONObject().apply {
                            put("id", rp.id)
                            put("name", rp.name)
                            put("slogan", rp.slogan)
                            put("phone", rp.phone)
                            put("address", rp.address)
                            put("logoBase64", rp.logoBase64)
                            put("cuisineType", rp.cuisineType)
                            put("rating", rp.rating)
                            put("website", rp.website)
                            put("isActive", rp.isActive)
                            put("updatedAt", rp.updatedAt)
                        })
                    }
                }
            )

            // Iterate and POST to Supabase (Upsert using merge-duplicates)
            for ((tableName, jsonArray) in tablesToPush) {
                if (jsonArray.length() == 0) continue // Skip empty tables

                val fullTableName = "$prefix$tableName"
                val requestUrl = "${baseUrl}rest/v1/$fullTableName"

                val body = jsonArray.toString().toRequestBody(mediaTypeJson)
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        return@withContext Result.failure(
                            Exception("Failed to upsert table '$fullTableName': HTTP ${response.code} - $bodyStr")
                        )
                    }
                }
            }

            Result.success("All local offline records successfully pushed & synchronized to Supabase cloud storage.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Pulls all cloud rows from Supabase, completely replacing the local SQLite data.
     */
    suspend fun pullFromSupabase(
        context: Context,
        url: String,
        key: String,
        prefix: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are not configured under Cloud settings."))
        }

        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val db = RestaurantDatabase.getDatabase(context)
        val dao = db.restaurantDao()

        val tableNames = listOf(
            "users",
            "raw_inventory",
            "purchases",
            "menu_items",
            "bom_recipes",
            "production_batches",
            "orders",
            "order_items",
            "restaurant_profiles"
        )

        val pulledData = mutableMapOf<String, JSONArray>()

        try {
            // Fetch each table from Supabase
            for (tableName in tableNames) {
                val fullTableName = "$prefix$tableName"
                val requestUrl = "${baseUrl}rest/v1/$fullTableName?select=*"

                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $key")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        return@withContext Result.failure(
                            Exception("Failed to fetch table '$fullTableName': HTTP ${response.code} - $bodyStr")
                        )
                    }
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    pulledData[tableName] = jsonArray
                }
            }

            // Perform atomic local restoration
            db.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    dao.deleteAllOrders()
                    dao.deleteAllOrderItems()
                    dao.deleteAllProductionBatches()
                    dao.deleteAllPurchases()
                    dao.deleteAllRawInventory()
                    dao.deleteAllMenuItems()
                    dao.deleteAllBomRecipes()
                    dao.deleteAllUsers()
                    dao.deleteAllRestaurantProfiles()

                    // Restore Users
                    val usersArr = pulledData["users"]
                    if (usersArr != null) {
                        for (i in 0 until usersArr.length()) {
                            val obj = usersArr.getJSONObject(i)
                            dao.insertUser(User(
                                id = obj.optInt("id", 0),
                                username = obj.getString("username"),
                                role = obj.getString("role"),
                                password = obj.optString("password", "1234")
                            ))
                        }
                    }

                    // Restore Raw Materials
                    val rmArr = pulledData["raw_inventory"]
                    if (rmArr != null) {
                        for (i in 0 until rmArr.length()) {
                            val obj = rmArr.getJSONObject(i)
                            dao.insertRawMaterial(RawMaterial(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                quantity = obj.getDouble("quantity"),
                                unit = obj.getString("unit"),
                                avgCostPerUnit = obj.optDouble("avgCostPerUnit", 0.0),
                                lowStockThreshold = obj.optDouble("lowStockThreshold", 0.0)
                            ))
                        }
                    }

                    // Restore Purchases
                    val purchArr = pulledData["purchases"]
                    if (purchArr != null) {
                        for (i in 0 until purchArr.length()) {
                            val obj = purchArr.getJSONObject(i)
                            dao.insertPurchase(Purchase(
                                id = obj.optInt("id", 0),
                                rawItemId = obj.getInt("rawItemId"),
                                rawItemName = obj.getString("rawItemName"),
                                quantity = obj.getDouble("quantity"),
                                cost = obj.getDouble("cost"),
                                purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis())
                            ))
                        }
                    }

                    // Restore Menu Items
                    val miArr = pulledData["menu_items"]
                    if (miArr != null) {
                        for (i in 0 until miArr.length()) {
                            val obj = miArr.getJSONObject(i)
                            dao.insertMenuItem(MenuItem(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                price = obj.getDouble("price"),
                                taxPercent = obj.optDouble("taxPercent", 0.0),
                                availableQuantity = obj.optDouble("availableQuantity", 0.0),
                                recipeYield = obj.optDouble("recipeYield", 12.0),
                                hasBom = obj.optBoolean("hasBom", false),
                                category = obj.optString("category", "General"),
                                imageBase64 = obj.optString("imageBase64", "")
                            ))
                        }
                    }

                    // Restore BOM Recipes
                    val bomArr = pulledData["bom_recipes"]
                    if (bomArr != null) {
                        for (i in 0 until bomArr.length()) {
                            val obj = bomArr.getJSONObject(i)
                            dao.insertBomIngredient(BomIngredient(
                                id = obj.optInt("id", 0),
                                menuItemId = obj.getInt("menuItemId"),
                                rawItemId = obj.getInt("rawItemId"),
                                rawItemName = obj.getString("rawItemName"),
                                requiredQuantity = obj.getDouble("requiredQuantity"),
                                unit = obj.getString("unit")
                            ))
                        }
                    }

                    // Restore Production Batches
                    val pbArr = pulledData["production_batches"]
                    if (pbArr != null) {
                        for (i in 0 until pbArr.length()) {
                            val obj = pbArr.getJSONObject(i)
                            dao.insertProductionBatch(ProductionBatch(
                                id = obj.optInt("id", 0),
                                menuItemId = obj.getInt("menuItemId"),
                                menuItemName = obj.getString("menuItemName"),
                                producedQuantity = obj.getDouble("producedQuantity"),
                                totalCost = obj.getDouble("totalCost"),
                                productionDate = obj.optLong("productionDate", System.currentTimeMillis())
                            ))
                        }
                    }

                    // Restore Orders
                    val oArr = pulledData["orders"]
                    if (oArr != null) {
                        for (i in 0 until oArr.length()) {
                            val obj = oArr.getJSONObject(i)
                            val creditVal = if (obj.isNull("creditDueDate")) null else obj.getLong("creditDueDate")
                            dao.insertOrder(Order(
                                id = obj.optInt("id", 0),
                                orderNumber = obj.getString("orderNumber"),
                                type = obj.getString("type"),
                                status = obj.getString("status"),
                                totalItemsQuantity = obj.getDouble("totalItemsQuantity"),
                                subtotal = obj.getDouble("subtotal"),
                                taxAmount = obj.getDouble("taxAmount"),
                                totalAmount = obj.getDouble("totalAmount"),
                                orderDate = obj.optLong("orderDate", System.currentTimeMillis()),
                                customerName = obj.optString("customerName", ""),
                                customerPhone = obj.optString("customerPhone", ""),
                                paymentMethod = obj.optString("paymentMethod", "Cash"),
                                creditDueDate = creditVal,
                                paymentStatus = obj.optString("paymentStatus", "Paid"),
                                customerAddress = obj.optString("customerAddress", ""),
                                riderName = obj.optString("riderName", ""),
                                riderPhone = obj.optString("riderPhone", ""),
                                riderBikeNumber = obj.optString("riderBikeNumber", ""),
                                riderCharges = obj.optDouble("riderCharges", 0.0)
                            ))
                        }
                    }

                    // Restore Order Items
                    val oiArr = pulledData["order_items"]
                    if (oiArr != null) {
                        for (i in 0 until oiArr.length()) {
                            val obj = oiArr.getJSONObject(i)
                            dao.insertOrderItem(OrderItem(
                                id = obj.optInt("id", 0),
                                orderId = obj.getInt("orderId"),
                                menuItemId = obj.getInt("menuItemId"),
                                menuItemName = obj.getString("menuItemName"),
                                quantity = obj.getDouble("quantity"),
                                unitPrice = obj.getDouble("unitPrice"),
                                taxPercent = obj.optDouble("taxPercent", 0.0),
                                taxAmount = obj.optDouble("taxAmount", 0.0),
                                totalAmount = obj.getDouble("totalAmount")
                            ))
                        }
                    }

                    // Restore Restaurant Profiles
                    val rpArr = pulledData["restaurant_profiles"]
                    if (rpArr != null) {
                        for (i in 0 until rpArr.length()) {
                            val obj = rpArr.getJSONObject(i)
                            dao.insertRestaurantProfile(RestaurantProfile(
                                id = obj.optInt("id", 0),
                                name = obj.getString("name"),
                                slogan = obj.optString("slogan", ""),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                logoBase64 = obj.optString("logoBase64", ""),
                                cuisineType = obj.optString("cuisineType", "General"),
                                rating = obj.optDouble("rating", 5.0),
                                website = obj.optString("website", ""),
                                isActive = obj.optBoolean("isActive", false),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            ))
                        }
                    }
                }
            }

            Result.success("All tables successfully pulled from Supabase cloud storage and synced locally.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Bidirectional synchronization: pulls remote data, merges with local SQLite transactions,
     * and pushes the complete unified database state back up to Supabase.
     */
    suspend fun syncBothSidesWithSupabase(
        context: Context,
        url: String,
        key: String,
        prefix: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are not configured under Cloud settings."))
        }

        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val db = RestaurantDatabase.getDatabase(context)
        val dao = db.restaurantDao()

        val tableNames = listOf(
            "users",
            "raw_inventory",
            "purchases",
            "menu_items",
            "bom_recipes",
            "production_batches",
            "orders",
            "order_items",
            "restaurant_profiles"
        )

        val pulledData = mutableMapOf<String, JSONArray>()

        try {
            // 1. Fetch each table from Supabase
            for (tableName in tableNames) {
                val fullTableName = "$prefix$tableName"
                val requestUrl = "${baseUrl}rest/v1/$fullTableName?select=*"

                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .addHeader("apikey", key)
                    .addHeader("Authorization", "Bearer $key")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        return@withContext Result.failure(
                            Exception("Failed to fetch table '$fullTableName': HTTP ${response.code} - $bodyStr")
                        )
                    }
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    pulledData[tableName] = jsonArray
                }
            }

            // 2. Perform atomic local insertion for items that do not exist locally
            db.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    // Restore Users
                    val localUsers = dao.getAllUsers().first()
                    val localUsersMap = localUsers.associateBy { it.id }
                    val usersArr = pulledData["users"]
                    if (usersArr != null) {
                        for (i in 0 until usersArr.length()) {
                            val obj = usersArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localUsersMap.containsKey(id)) {
                                dao.insertUser(User(
                                    id = id,
                                    username = obj.getString("username"),
                                    role = obj.getString("role"),
                                    password = obj.optString("password", "1234")
                                ))
                            }
                        }
                    }

                    // Restore Raw Materials
                    val localRaw = dao.getAllRawMaterials().first()
                    val localRawMap = localRaw.associateBy { it.id }
                    val rmArr = pulledData["raw_inventory"]
                    if (rmArr != null) {
                        for (i in 0 until rmArr.length()) {
                            val obj = rmArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localRawMap.containsKey(id)) {
                                dao.insertRawMaterial(RawMaterial(
                                    id = id,
                                    name = obj.getString("name"),
                                    quantity = obj.getDouble("quantity"),
                                    unit = obj.getString("unit"),
                                    avgCostPerUnit = obj.optDouble("avgCostPerUnit", 0.0),
                                    lowStockThreshold = obj.optDouble("lowStockThreshold", 0.0)
                                ))
                            }
                        }
                    }

                    // Restore Purchases
                    val localPurch = dao.getAllPurchases().first()
                    val localPurchMap = localPurch.associateBy { it.id }
                    val purchArr = pulledData["purchases"]
                    if (purchArr != null) {
                        for (i in 0 until purchArr.length()) {
                            val obj = purchArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localPurchMap.containsKey(id)) {
                                dao.insertPurchase(Purchase(
                                    id = id,
                                    rawItemId = obj.getInt("rawItemId"),
                                    rawItemName = obj.getString("rawItemName"),
                                    quantity = obj.getDouble("quantity"),
                                    cost = obj.getDouble("cost"),
                                    purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis())
                                ))
                            }
                        }
                    }

                    // Restore Menu Items
                    val localMenu = dao.getAllMenuItems().first()
                    val localMenuMap = localMenu.associateBy { it.id }
                    val miArr = pulledData["menu_items"]
                    if (miArr != null) {
                        for (i in 0 until miArr.length()) {
                            val obj = miArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localMenuMap.containsKey(id)) {
                                dao.insertMenuItem(MenuItem(
                                    id = id,
                                    name = obj.getString("name"),
                                    price = obj.getDouble("price"),
                                    taxPercent = obj.optDouble("taxPercent", 0.0),
                                    availableQuantity = obj.optDouble("availableQuantity", 0.0),
                                    recipeYield = obj.optDouble("recipeYield", 12.0),
                                    hasBom = obj.optBoolean("hasBom", false),
                                    category = obj.optString("category", "General"),
                                    imageBase64 = obj.optString("imageBase64", "")
                                ))
                            }
                        }
                    }

                    // Restore BOM Recipes
                    val localBom = mutableListOf<BomIngredient>()
                    for (mi in dao.getAllMenuItems().first()) {
                        localBom.addAll(dao.getBomIngredientsForMenuItem(mi.id).first())
                    }
                    val localBomMap = localBom.associateBy { it.id }
                    val bomArr = pulledData["bom_recipes"]
                    if (bomArr != null) {
                        for (i in 0 until bomArr.length()) {
                            val obj = bomArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localBomMap.containsKey(id)) {
                                dao.insertBomIngredient(BomIngredient(
                                    id = id,
                                    menuItemId = obj.getInt("menuItemId"),
                                    rawItemId = obj.getInt("rawItemId"),
                                    rawItemName = obj.getString("rawItemName"),
                                    requiredQuantity = obj.getDouble("requiredQuantity"),
                                    unit = obj.getString("unit")
                                ))
                            }
                        }
                    }

                    // Restore Production Batches
                    val localPB = dao.getAllProductionBatches().first()
                    val localPBMap = localPB.associateBy { it.id }
                    val pbArr = pulledData["production_batches"]
                    if (pbArr != null) {
                        for (i in 0 until pbArr.length()) {
                            val obj = pbArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localPBMap.containsKey(id)) {
                                dao.insertProductionBatch(ProductionBatch(
                                    id = id,
                                    menuItemId = obj.getInt("menuItemId"),
                                    menuItemName = obj.getString("menuItemName"),
                                    producedQuantity = obj.getDouble("producedQuantity"),
                                    totalCost = obj.getDouble("totalCost"),
                                    productionDate = obj.optLong("productionDate", System.currentTimeMillis())
                                ))
                            }
                        }
                    }

                    // Restore Orders
                    val localOrders = dao.getAllOrders().first()
                    val localOrdersMap = localOrders.associateBy { it.id }
                    val oArr = pulledData["orders"]
                    if (oArr != null) {
                        for (i in 0 until oArr.length()) {
                            val obj = oArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localOrdersMap.containsKey(id)) {
                                val creditVal = if (obj.isNull("creditDueDate")) null else obj.getLong("creditDueDate")
                                dao.insertOrder(Order(
                                    id = id,
                                    orderNumber = obj.getString("orderNumber"),
                                    type = obj.getString("type"),
                                    status = obj.getString("status"),
                                    totalItemsQuantity = obj.getDouble("totalItemsQuantity"),
                                    subtotal = obj.getDouble("subtotal"),
                                    taxAmount = obj.getDouble("taxAmount"),
                                    totalAmount = obj.getDouble("totalAmount"),
                                    orderDate = obj.optLong("orderDate", System.currentTimeMillis()),
                                    customerName = obj.optString("customerName", ""),
                                    customerPhone = obj.optString("customerPhone", ""),
                                    paymentMethod = obj.optString("paymentMethod", "Cash"),
                                    creditDueDate = creditVal,
                                    paymentStatus = obj.optString("paymentStatus", "Paid"),
                                    customerAddress = obj.optString("customerAddress", ""),
                                    riderName = obj.optString("riderName", ""),
                                    riderPhone = obj.optString("riderPhone", ""),
                                    riderBikeNumber = obj.optString("riderBikeNumber", ""),
                                    riderCharges = obj.optDouble("riderCharges", 0.0)
                                ))
                            }
                        }
                    }

                    // Restore Order Items
                    val localOrderItems = mutableListOf<OrderItem>()
                    for (o in dao.getAllOrders().first()) {
                        localOrderItems.addAll(dao.getOrderItemsForOrder(o.id).first())
                    }
                    val localOrderItemsMap = localOrderItems.associateBy { it.id }
                    val oiArr = pulledData["order_items"]
                    if (oiArr != null) {
                        for (i in 0 until oiArr.length()) {
                            val obj = oiArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localOrderItemsMap.containsKey(id)) {
                                dao.insertOrderItem(OrderItem(
                                    id = id,
                                    orderId = obj.getInt("orderId"),
                                    menuItemId = obj.getInt("menuItemId"),
                                    menuItemName = obj.getString("menuItemName"),
                                    quantity = obj.getDouble("quantity"),
                                    unitPrice = obj.getDouble("unitPrice"),
                                    taxPercent = obj.optDouble("taxPercent", 0.0),
                                    taxAmount = obj.optDouble("taxAmount", 0.0),
                                    totalAmount = obj.getDouble("totalAmount")
                                ))
                            }
                        }
                    }

                    // Restore Restaurant Profiles
                    val localProfiles = dao.getAllRestaurantProfiles().first()
                    val localProfilesMap = localProfiles.associateBy { it.id }
                    val rpArr = pulledData["restaurant_profiles"]
                    if (rpArr != null) {
                        for (i in 0 until rpArr.length()) {
                            val obj = rpArr.getJSONObject(i)
                            val id = obj.optInt("id", 0)
                            if (!localProfilesMap.containsKey(id)) {
                                dao.insertRestaurantProfile(RestaurantProfile(
                                    id = id,
                                    name = obj.getString("name"),
                                    slogan = obj.optString("slogan", ""),
                                    phone = obj.optString("phone", ""),
                                    address = obj.optString("address", ""),
                                    logoBase64 = obj.optString("logoBase64", ""),
                                    cuisineType = obj.optString("cuisineType", "General"),
                                    rating = obj.optDouble("rating", 5.0),
                                    website = obj.optString("website", ""),
                                    isActive = obj.optBoolean("isActive", false),
                                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                                ))
                            }
                        }
                    }
                }
            }

            // 3. Push fully merged local SQLite dataset back up to Supabase to make both in perfect sync
            val pushResult = pushToSupabase(context, url, key, prefix)
            if (pushResult.isFailure) {
                return@withContext Result.failure(Exception("Offline data merged locally, but push sync back to Supabase failed: ${pushResult.exceptionOrNull()?.message}"))
            }

            Result.success("Two-Way Offline/Online Sync Successful! Both local terminal SQLite and remote Supabase Cloud are now in perfect synchronization.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Generates standard SQL scripts to create all matching tables in Supabase SQL editor.
     */
    fun generateSupabaseSQL(prefix: String): String {
        return """
-- Dynamic SQL script to provision Supabase database tables for Bistro POS ERP.
-- Set Prefix: '$prefix'

CREATE TABLE IF NOT EXISTS "${prefix}users" (
    id SERIAL PRIMARY KEY,
    username TEXT NOT NULL,
    role TEXT NOT NULL,
    password TEXT DEFAULT '1234'
);

CREATE TABLE IF NOT EXISTS "${prefix}raw_inventory" (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    quantity DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    unit TEXT NOT NULL,
    "avgCostPerUnit" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "lowStockThreshold" DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS "${prefix}purchases" (
    id SERIAL PRIMARY KEY,
    "rawItemId" INTEGER NOT NULL,
    "rawItemName" TEXT NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    cost DOUBLE PRECISION NOT NULL,
    "purchaseDate" BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS "${prefix}menu_items" (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    "taxPercent" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "availableQuantity" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "recipeYield" DOUBLE PRECISION NOT NULL DEFAULT 12.0,
    "hasBom" BOOLEAN DEFAULT FALSE,
    category TEXT DEFAULT 'General',
    "imageBase64" TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS "${prefix}bom_recipes" (
    id SERIAL PRIMARY KEY,
    "menuItemId" INTEGER NOT NULL,
    "rawItemId" INTEGER NOT NULL,
    "rawItemName" TEXT NOT NULL,
    "requiredQuantity" DOUBLE PRECISION NOT NULL,
    unit TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS "${prefix}production_batches" (
    id SERIAL PRIMARY KEY,
    "menuItemId" INTEGER NOT NULL,
    "menuItemName" TEXT NOT NULL,
    "producedQuantity" DOUBLE PRECISION NOT NULL,
    "totalCost" DOUBLE PRECISION NOT NULL,
    "productionDate" BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS "${prefix}orders" (
    id SERIAL PRIMARY KEY,
    "orderNumber" TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL,
    "totalItemsQuantity" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    subtotal DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "taxAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "totalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "orderDate" BIGINT NOT NULL,
    "customerName" TEXT DEFAULT '',
    "customerPhone" TEXT DEFAULT '',
    "paymentMethod" TEXT DEFAULT 'Cash',
    "creditDueDate" BIGINT,
    "paymentStatus" TEXT DEFAULT 'Paid',
    "customerAddress" TEXT DEFAULT '',
    "riderName" TEXT DEFAULT '',
    "riderPhone" TEXT DEFAULT '',
    "riderBikeNumber" TEXT DEFAULT '',
    "riderCharges" DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE IF NOT EXISTS "${prefix}order_items" (
    id SERIAL PRIMARY KEY,
    "orderId" INTEGER NOT NULL,
    "menuItemId" INTEGER NOT NULL,
    "menuItemName" TEXT NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    "unitPrice" DOUBLE PRECISION NOT NULL,
    "taxPercent" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "taxAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "totalAmount" DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS "${prefix}restaurant_profiles" (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    slogan TEXT,
    phone TEXT,
    address TEXT,
    "logoBase64" TEXT,
    "cuisineType" TEXT NOT NULL DEFAULT 'General',
    rating DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    website TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" BIGINT NOT NULL
);

-- Enable row-level security or grant broad access for API tokens
ALTER TABLE "${prefix}users" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}raw_inventory" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}purchases" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}menu_items" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}bom_recipes" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}production_batches" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}orders" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}order_items" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "${prefix}restaurant_profiles" ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow anon access" ON "${prefix}users" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}raw_inventory" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}purchases" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}menu_items" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}bom_recipes" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}production_batches" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}orders" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}order_items" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon access" ON "${prefix}restaurant_profiles" AS PERMISSIVE FOR ALL TO public USING (true) WITH CHECK (true);
        """.trimIndent()
    }

    /**
     * Generates SQL script to alter existing menu_items table in Supabase
     * to support category and imageBase64 if they were created under a previous version.
     */
    fun generateSupabaseUpdateSQL(prefix: String): String {
        return """
            -- SQL script to update existing database schema in Supabase.
            -- Run this in your Supabase SQL Editor if you already created the tables previously.
            -- This will add 'category' and 'imageBase64' columns to your matching menu_items table.
            
            ALTER TABLE "${prefix}menu_items" ADD COLUMN IF NOT EXISTS "category" TEXT DEFAULT 'General';
            ALTER TABLE "${prefix}menu_items" ADD COLUMN IF NOT EXISTS "imageBase64" TEXT DEFAULT '';
        """.trimIndent()
    }

    fun shareCategoryMenuQuery(prefix: String, category: String, url: String): String {
        val fullTableName = "${prefix}menu_items"
        val requestUrl = "${if (url.endsWith("/")) url else "$url/"}rest/v1/$fullTableName?select=*&category=eq.${java.net.URLEncoder.encode(category, "UTF-8")}"
        return """
            -- SQL query to fetch menu items by category:
            SELECT * FROM "$fullTableName" WHERE category = '$category';
            
            -- API Query URL (HTTP GET):
            $requestUrl
        """.trimIndent()
    }

    suspend fun queryMenuItemsByCategoryFromSupabase(
        url: String,
        key: String,
        prefix: String,
        category: String
    ): Result<List<MenuItem>> = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are not configured under Cloud settings."))
        }
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val fullTableName = "${prefix}menu_items"
        
        val requestUrl = "${baseUrl}rest/v1/$fullTableName?select=*&category=eq.${java.net.URLEncoder.encode(category, "UTF-8")}"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Failed to query menu items: HTTP ${response.code} - $bodyStr"))
                }
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<MenuItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(MenuItem(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        price = obj.getDouble("price"),
                        taxPercent = obj.optDouble("taxPercent", 0.0),
                        availableQuantity = obj.optDouble("availableQuantity", 0.0),
                        recipeYield = obj.optDouble("recipeYield", 12.0),
                        hasBom = obj.optBoolean("hasBom", false),
                        category = obj.optString("category", "General"),
                        imageBase64 = obj.optString("imageBase64", "")
                    ))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryRestaurantProfilesFromSupabase(
        url: String,
        key: String,
        prefix: String,
        searchQuery: String? = null,
        cuisineQuery: String? = null,
        minRatingQuery: Double? = null
    ): Result<List<RestaurantProfile>> = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) {
            return@withContext Result.failure(Exception("Supabase URL and API Key are not configured under Cloud settings."))
        }
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val fullTableName = "${prefix}restaurant_profiles"
        
        val queryParams = mutableListOf<String>()
        queryParams.add("select=*")
        if (!searchQuery.isNullOrBlank()) {
            queryParams.add("name=ilike.*${searchQuery.trim()}*")
        }
        if (!cuisineQuery.isNullOrBlank()) {
            queryParams.add("cuisineType=ilike.*${cuisineQuery.trim()}*")
        }
        if (minRatingQuery != null && minRatingQuery > 0.0) {
            queryParams.add("rating=gte.${minRatingQuery}")
        }
        
        val queryString = queryParams.joinToString("&")
        val requestUrl = "${baseUrl}rest/v1/$fullTableName?$queryString"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Failed to query profiles: HTTP ${response.code} - $bodyStr"))
                }
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<RestaurantProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(RestaurantProfile(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        slogan = obj.optString("slogan", ""),
                        phone = obj.optString("phone", ""),
                        address = obj.optString("address", ""),
                        logoBase64 = obj.optString("logoBase64", ""),
                        cuisineType = obj.optString("cuisineType", "General"),
                        rating = obj.optDouble("rating", 5.0),
                        website = obj.optString("website", ""),
                        isActive = obj.optBoolean("isActive", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    ))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
