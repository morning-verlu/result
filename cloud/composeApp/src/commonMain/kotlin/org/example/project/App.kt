package cn.verlu.cloud

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import cn.verlu.cloud.di.AppGraph
import cn.verlu.cloud.di.ensureKoinStarted
import cn.verlu.cloud.navigation.CloudAppRoot
import org.koin.core.context.GlobalContext

@Composable
@Preview
fun App() {
    MaterialTheme {
        val graph = remember {
            ensureKoinStarted()
            GlobalContext.get().get<AppGraph>()
        }
        CloudAppRoot(graph)
    }
}