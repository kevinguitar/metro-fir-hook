package com.example.metrofirhook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.metrofirhook.ui.theme.MetroFirHookTheme
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory

class MainActivity : ComponentActivity() {

    @Inject
    private lateinit var viewModel: MainViewModel

    private val graph by lazy {
        createGraphFactory<MainActivityGraph.Factory>().create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetroFirHookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Text(text = viewModel.greeting)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MetroFirHookTheme {
        Greeting("Android")
    }
}