package dev.mayankmkh.basekmpproject.shared.features.list.domain

internal data class ItemsModel(val items: List<Item>)

internal data class Item(val id: String, val title: String, val text: String)
