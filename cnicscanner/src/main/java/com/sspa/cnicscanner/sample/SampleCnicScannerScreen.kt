package com.sspa.cnicscanner.sample

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sspa.cnicscanner.CnicScanner
import com.sspa.cnicscanner.core.ImageSource
import com.sspa.cnicscanner.entities.CnicEntity
import kotlinx.coroutines.launch

/**
 * Sample screen demonstrating CNIC Scanner usage with Jetpack Compose.
 * 
 * This screen shows how to:
 * - Initialize CnicScanner
 * - Scan CNIC from different sources (Camera, Gallery, Document Scanner)
 * - Display scanned CNIC data
 * - Show captured images
 * 
 * Usage in your Composable:
 * ```kotlin
 * @Composable
 * fun MyApp() {
 *     SampleCnicScannerScreen()
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleCnicScannerScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    
    var cnicData by remember { mutableStateOf<CnicEntity?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentScanSide by remember { mutableStateOf("Front") }
    
    // Initialize scanner
    val scanner = remember {
        activity?.let { act ->
            CnicScanner(
                context = context,
                activity = act,
                ocrParser = SampleCnicOcrParser()
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "CNIC Scanner Sample",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Scan Side Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Select CNIC Side to Scan:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentScanSide == "Front",
                            onClick = { currentScanSide = "Front" },
                            label = { Text("Front") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentScanSide == "Back",
                            onClick = { currentScanSide = "Back" },
                            label = { Text("Back") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Scan Buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose Scan Method:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Button(
                        onClick = {
                            if (scanner != null && activity != null) {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val result = scanner.scanImage(
                                            ImageSource.CAMERA,
                                            isBackScan = currentScanSide == "Back"
                                        )
                                        cnicData = result
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && scanner != null
                    ) {
                        Text("📷 Scan from Camera")
                    }
                    
                    Button(
                        onClick = {
                            if (scanner != null && activity != null) {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val result = scanner.scanImage(
                                            ImageSource.GALLERY,
                                            isBackScan = currentScanSide == "Back"
                                        )
                                        cnicData = result
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && scanner != null
                    ) {
                        Text("🖼️ Select from Gallery")
                    }
                    
                    Button(
                        onClick = {
                            if (scanner != null && activity != null) {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val result = scanner.scanImage(
                                            ImageSource.DOCUMENT_SCANNER,
                                            isBackScan = currentScanSide == "Back"
                                        )
                                        cnicData = result
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && scanner != null
                    ) {
                        Text("📄 Document Scanner")
                    }
                }
            }
            
            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Display scanned data
            cnicData?.let { data ->
                CnicDataDisplay(data)
            }
        }
    }
}

@Composable
private fun CnicDataDisplay(cnicData: CnicEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Scanned CNIC Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Card Type
            if (cnicData.cardType.isNotEmpty()) {
                DataField(label = "Card Type", value = cnicData.cardType)
            }
            
            // CNIC Number
            if (cnicData.cnic.isNotEmpty()) {
                DataField(label = "CNIC Number", value = cnicData.cnic)
            }
            
            // Name
            if (cnicData.name.isNotEmpty()) {
                DataField(label = "Name", value = cnicData.name)
            }
            
            // Father's Name
            if (cnicData.father_name.isNotEmpty()) {
                DataField(label = "Father's Name", value = cnicData.father_name)
            }
            
            // Date of Birth
            if (cnicData.date_of_birth.isNotEmpty()) {
                DataField(label = "Date of Birth", value = cnicData.date_of_birth)
            }
            
            // Gender
            if (cnicData.gender.isNotEmpty()) {
                DataField(label = "Gender", value = cnicData.gender)
            }
            
            // Country
            if (cnicData.cnicHolderCountry.isNotEmpty()) {
                DataField(label = "Country", value = cnicData.cnicHolderCountry)
            }
            
            // Issue Date
            if (cnicData.cnic_issue_date.isNotEmpty()) {
                DataField(label = "Issue Date", value = cnicData.cnic_issue_date)
            }
            
            // Expiry Date
            if (cnicData.cnic_expiry.isNotEmpty()) {
                DataField(label = "Expiry Date", value = cnicData.cnic_expiry)
            }
            
            // Present Address
            if (cnicData.present_address.isNotEmpty()) {
                DataField(label = "Present Address", value = cnicData.present_address)
            }
            
            // Permanent Address
            if (cnicData.permanent_address.isNotEmpty()) {
                DataField(label = "Permanent Address", value = cnicData.permanent_address)
            }
            
            // Display captured images
            Spacer(modifier = Modifier.height(16.dp))
            
            // Front Image
            cnicData.cnic_front?.let { frontUri ->
                Text(
                    text = "Front Image:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(frontUri)),
                    contentDescription = "CNIC Front",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Back Image
            cnicData.cnic_back?.let { backUri ->
                Text(
                    text = "Back Image:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(backUri)),
                    contentDescription = "CNIC Back",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Completion status
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (cnicData.isComplete()) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.tertiaryContainer
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (cnicData.isComplete()) 
                        "✅ All fields completed" 
                    else 
                        "⚠️ Some fields missing",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DataField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium
        )
        Divider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
        )
    }
}
