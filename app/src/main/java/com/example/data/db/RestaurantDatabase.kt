package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.RestaurantDao
import com.example.data.model.*

@Database(
    entities = [
        User::class,
        RawMaterial::class,
        Purchase::class,
        MenuItem::class,
        BomIngredient::class,
        ProductionBatch::class,
        Order::class,
        OrderItem::class,
        RestaurantProfile::class
    ],
    version = 6,
    exportSchema = false
)
abstract class RestaurantDatabase : RoomDatabase() {

    abstract fun restaurantDao(): RestaurantDao

    companion object {
        @Volatile
        private var INSTANCE: RestaurantDatabase? = null

        fun getDatabase(context: Context): RestaurantDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RestaurantDatabase::class.java,
                    "restaurant_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
