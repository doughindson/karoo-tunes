package com.doughindson.karootunes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

const val TAG = "karoo-tunes"

/** Process-wide scope for work that must outlive any single screen, view or service. */
class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
