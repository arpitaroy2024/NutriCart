package com.example.nutricart.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.Account
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.ConfirmDialog
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.components.TagTone
import com.example.nutricart.ui.initialsOf
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date

private val AvatarSize = 96.dp
private val AvatarRing = 4.dp

// SCR-09. A bottom-navigation root: no back button, and the bar is drawn by the nav host.
@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    ProfileContent(
        state = state,
        onEditProfile = onEditProfile,
        onLogout = viewModel::logout
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = NutriCartTheme.colors
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.logout_title),
            message = stringResource(R.string.logout_message),
            confirmLabel = stringResource(R.string.action_log_out),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
            confirmColor = colors.danger
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.nav_profile),
                style = NutriCartTheme.typography.headline,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xl))

            val account = state.account
            if (account != null) {
                AccountHeader(account)
                Spacer(modifier = Modifier.height(Spacing.xl))
                DetailsCard(account = account, profile = state.profile)
            }

            val profile = state.profile
            if (profile != null) {
                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionLabel(text = stringResource(R.string.label_allergies))
                Spacer(modifier = Modifier.height(Spacing.xs))
                // Listed in the order used on the setup screen, with the typed entries last
                val allergies = Allergen.entries.filter { it in profile.allergies }
                    .map { stringResource(it.labelRes()) } + profile.customAllergies
                if (allergies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.allergies_none),
                        style = NutriCartTheme.typography.body,
                        color = colors.onSurfaceMuted
                    )
                } else {
                    ChipGroup(labels = allergies, tone = TagTone.Allergy)
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
                SectionLabel(text = stringResource(R.string.label_conditions))
                Spacer(modifier = Modifier.height(Spacing.xs))
                val conditions = HealthCondition.entries.filter { it in profile.conditions }
                    .map { stringResource(it.labelRes()) } + profile.customConditions
                // "None" is shown rather than hidden
                ChipGroup(
                    labels = conditions.ifEmpty { listOf(stringResource(R.string.conditions_none)) },
                    tone = TagTone.Condition
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))
            NutriFilledButton(text = stringResource(R.string.edit_profile), onClick = onEditProfile)
            Spacer(modifier = Modifier.height(Spacing.sm))
            NutriOutlinedButton(
                text = stringResource(R.string.action_log_out),
                onClick = { confirmLogout = true },
                color = colors.danger
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

// Wrapping chips; a long typed value is cut with an ellipsis instead of overflowing
@Composable
private fun ChipGroup(labels: List<String>, tone: TagTone) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        labels.forEach { TagChip(text = it, tone = tone) }
    }
}

@Composable
private fun AccountHeader(account: Account) {
    val colors = NutriCartTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The name is read out below, so the initials are skipped by screen readers
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .border(AvatarRing, colors.surfaceCard, CircleShape)
                .padding(AvatarRing)
                .background(colors.primaryContainer, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(account.name),
                style = NutriCartTheme.typography.headline,
                color = colors.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = account.name,
            style = NutriCartTheme.typography.headline,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                R.string.profile_member_since,
                SimpleDateFormat("MMM yyyy", Locale.current.platformLocale).format(Date(account.createdAt))
            ),
            style = NutriCartTheme.typography.caption,
            color = colors.onSurfaceMuted
        )
    }
}

@Composable
private fun DetailsCard(account: Account, profile: ProfileEntity?) {
    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.md)
    ) {
        DetailRow(label = stringResource(R.string.field_email), value = account.email)
        if (profile != null) {
            HorizontalDivider(thickness = Sizes.outline, color = NutriCartTheme.colors.outline)
            DetailRow(label = stringResource(R.string.label_region), value = profile.region)
            HorizontalDivider(thickness = Sizes.outline, color = NutriCartTheme.colors.outline)
            DetailRow(
                label = stringResource(R.string.label_household),
                value = pluralStringResource(R.plurals.household_count, profile.householdSize, profile.householdSize)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = NutriCartTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = NutriCartTheme.typography.caption,
            color = colors.onSurfaceMuted
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfilePreview() {
    NutriCartTheme {
        ProfileContent(
            state = ProfileUiState(
                account = Account(1, "Name Surname", "name@example.com", 1_780_000_000_000),
                profile = ProfileEntity(
                    accountId = 1,
                    region = "Rangpur Division",
                    householdSize = 4,
                    allergies = setOf(Allergen.Peanuts, Allergen.Shellfish),
                    customAllergies = listOf("Mustard", "Mango"),
                    conditions = setOf(HealthCondition.Diabetes, HealthCondition.Hypertension)
                )
            ),
            onEditProfile = {},
            onLogout = {}
        )
    }
}
