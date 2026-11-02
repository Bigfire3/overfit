package app.overfit.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.overfit.AppContainer
import app.overfit.OverfitApp

/** ViewModel-Factory für die manuelle DI: Zugriff auf [AppContainer] und [SavedStateHandle]. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (container: AppContainer, handle: SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create((this[APPLICATION_KEY] as OverfitApp).container, createSavedStateHandle()) }
}
