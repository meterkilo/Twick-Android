package com.example.chatterinomobile.di

import com.example.chatterinomobile.ui.chat.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { ChatViewModel(get(), get()) }
}
