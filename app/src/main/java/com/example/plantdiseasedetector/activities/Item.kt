package com.example.plantdiseasedetector.activities

data class Item(
    val id:Int,
    val image:String,
    val confidence:Int,
    val name:String,
    val description:String
)
