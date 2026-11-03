package com.example.ui.screens.history

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalculationRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

  private val dao = AppDatabase.getDatabase(application).calculationHistoryDao()

  private val _selectedFilter = MutableStateFlow("ALL")
  val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

  val historyList: StateFlow<List<CalculationRecord>> = _selectedFilter
    .flatMapLatest { filter ->
      if (filter == "ALL") {
        dao.getAll()
      } else {
        dao.getByCategory(filter)
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val favoritesList: StateFlow<List<CalculationRecord>> = dao.getFavorites()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun setFilter(filter: String) {
    _selectedFilter.value = filter
  }

  fun toggleFavorite(record: CalculationRecord) {
    viewModelScope.launch {
      dao.updateFavorite(record.id, !record.isFavorite)
    }
  }

  fun delete(record: CalculationRecord) {
    viewModelScope.launch {
      dao.delete(record)
    }
  }

  fun clearAll() {
    viewModelScope.launch {
      dao.clearAll()
    }
  }

  fun shareRecord(context: Context, record: CalculationRecord) {
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, "${record.title}\n\n${record.detailsText}\n\nCalculated using OmniCalc")
      putExtra(Intent.EXTRA_SUBJECT, record.title)
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Calculation")
    context.startActivity(shareIntent)
  }
}
