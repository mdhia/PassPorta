package org.shadowgrove.passporta.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalPlay
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A selectable symbol for a pass.
 *
 * @param key stable key; only this is stored. The mapping to the [ImageVector] happens only
 *   when displaying - this keeps the database independent of the icon library.
 * @param label display name and also the first search term.
 * @param keywords further search terms, German and English, consistently lowercase.
 */
@Immutable
data class PassIcon(
    val key: String,
    val label: String,
    val keywords: List<String>,
    val image: ImageVector,
)

/**
 * Curated selection from the Material Symbols.
 *
 * Deliberately a fixed list instead of reflection over all roughly 3000 symbols: for one, R8
 * cannot shrink a library accessed via reflection, so the app would be noticeably larger. For
 * another, a search for "train" among thirty matching symbols is more useful than among three
 * thousand mostly unsuitable ones.
 */
object PassIconLibrary {

    val all: List<PassIcon> = listOf(
        // --- Transport ---
        icon("train", "Zug", Icons.Filled.Train, "bahn", "zug", "db", "ice", "train", "railway", "rail"),
        icon("subway", "U-Bahn", Icons.Filled.DirectionsSubway, "ubahn", "u-bahn", "metro", "subway", "sbahn", "s-bahn"),
        icon("tram", "Straßenbahn", Icons.Filled.Tram, "tram", "strassenbahn", "straßenbahn", "bim"),
        icon("bus", "Bus", Icons.Filled.DirectionsBus, "bus", "reisebus", "coach", "fernbus"),
        icon("flight", "Flug", Icons.Filled.Flight, "flug", "flight", "airline", "boarding", "bordkarte", "airport", "flughafen"),
        icon("boat", "Schiff", Icons.Filled.DirectionsBoat, "schiff", "faehre", "fähre", "ferry", "boot", "cruise"),
        icon("car", "Auto", Icons.Filled.DirectionsCar, "auto", "car", "mietwagen", "rental"),
        icon("taxi", "Taxi", Icons.Filled.LocalTaxi, "taxi", "cab", "uber"),
        icon("bike", "Fahrrad", Icons.Filled.DirectionsBike, "fahrrad", "bike", "rad", "cycling"),
        icon("parking", "Parken", Icons.Filled.LocalParking, "parken", "parking", "parkhaus", "garage"),
        icon("fuel", "Tankstelle", Icons.Filled.LocalGasStation, "tanken", "tankstelle", "fuel", "gas", "benzin"),
        icon("luggage", "Gepäck", Icons.Filled.Luggage, "gepaeck", "gepäck", "koffer", "luggage", "baggage"),
        icon("map", "Karte", Icons.Filled.Map, "karte", "map", "route", "reise"),

        // --- Tickets and events ---
        icon("ticket", "Ticket", Icons.Filled.ConfirmationNumber, "ticket", "eintritt", "einlass", "karte", "admission"),
        icon("event", "Veranstaltung", Icons.Filled.LocalActivity, "veranstaltung", "event", "festival", "show"),
        icon("theater", "Theater", Icons.Filled.Theaters, "theater", "buehne", "bühne", "oper", "musical"),
        icon("cinema", "Kino", Icons.Filled.Movie, "kino", "cinema", "film", "movie"),
        icon("concert", "Konzert", Icons.Filled.MusicNote, "konzert", "concert", "musik", "music", "band"),
        icon("sports", "Sport", Icons.Filled.SportsSoccer, "sport", "fussball", "fußball", "stadion", "stadium", "spiel", "soccer"),
        icon("play", "Freizeit", Icons.Filled.LocalPlay, "freizeit", "park", "attraktion", "zoo", "museum"),
        icon("museum", "Museum", Icons.Filled.Museum, "museum", "ausstellung", "galerie", "exhibition"),
        icon("party", "Feier", Icons.Filled.Celebration, "feier", "party", "celebration", "silvester"),

        // --- Shopping and loyalty ---
        icon("loyalty", "Kundenkarte", Icons.Filled.Loyalty, "kundenkarte", "treue", "loyalty", "bonus", "punkte", "payback"),
        icon("shopping", "Einkauf", Icons.Filled.ShoppingCart, "einkauf", "shopping", "warenkorb", "markt"),
        icon("grocery", "Supermarkt", Icons.Filled.LocalGroceryStore, "supermarkt", "grocery", "lebensmittel", "rewe", "edeka", "aldi", "lidl"),
        icon("store", "Geschäft", Icons.Filled.Storefront, "geschaeft", "geschäft", "laden", "store", "shop", "filiale"),
        icon("mall", "Einkaufszentrum", Icons.Filled.LocalMall, "einkaufszentrum", "mall", "center", "outlet"),
        icon("offer", "Rabatt", Icons.Filled.LocalOffer, "rabatt", "gutschein", "coupon", "offer", "aktion", "sale"),
        icon("giftcard", "Geschenkkarte", Icons.Filled.CardGiftcard, "geschenk", "gift", "geschenkkarte", "giftcard"),
        icon("voucher", "Gutschein", Icons.Filled.Redeem, "gutschein", "voucher", "einloesen", "einlösen", "redeem"),

        // --- Money ---
        icon("card", "Bankkarte", Icons.Filled.CreditCard, "karte", "kreditkarte", "credit", "card", "bank", "ec"),
        icon("bank", "Bank", Icons.Filled.AccountBalance, "bank", "sparkasse", "konto", "account"),
        icon("wallet", "Geldbörse", Icons.Filled.AccountBalanceWallet, "geldboerse", "geldbörse", "wallet", "guthaben"),
        icon("payment", "Zahlung", Icons.Filled.Payment, "zahlung", "payment", "bezahlen", "rechnung"),

        // --- Travel and stays ---
        icon("hotel", "Hotel", Icons.Filled.Hotel, "hotel", "uebernachtung", "übernachtung", "zimmer", "hostel", "buchung"),
        icon("beach", "Urlaub", Icons.Filled.BeachAccess, "urlaub", "strand", "beach", "ferien", "holiday"),
        icon("park", "Park", Icons.Filled.Park, "park", "garten", "natur", "wandern"),
        icon("key", "Zugang", Icons.Filled.Key, "zugang", "schluessel", "schlüssel", "key", "access", "tuer", "tür"),

        // --- Food and drink ---
        icon("restaurant", "Restaurant", Icons.Filled.Restaurant, "restaurant", "essen", "gastro", "dinner", "food"),
        icon("cafe", "Café", Icons.Filled.LocalCafe, "cafe", "café", "kaffee", "coffee", "baeckerei", "bäckerei"),
        icon("bar", "Bar", Icons.Filled.LocalBar, "bar", "cocktail", "drinks", "kneipe", "club"),
        icon("pizza", "Lieferung", Icons.Filled.LocalPizza, "pizza", "lieferung", "delivery", "italiener"),
        icon("fastfood", "Imbiss", Icons.Filled.Fastfood, "imbiss", "fastfood", "burger", "schnellrestaurant"),

        // --- Health and sports ---
        icon("health", "Gesundheit", Icons.Filled.LocalHospital, "gesundheit", "arzt", "klinik", "krankenhaus", "apotheke", "versicherung", "krankenkasse"),
        icon("gym", "Fitness", Icons.Filled.FitnessCenter, "fitness", "gym", "studio", "sportstudio", "training"),
        icon("pool", "Schwimmbad", Icons.Filled.Pool, "schwimmbad", "pool", "bad", "therme", "sauna"),
        icon("spa", "Wellness", Icons.Filled.Spa, "wellness", "spa", "massage", "kosmetik"),

        // --- Education and work ---
        icon("school", "Bildung", Icons.Filled.School, "schule", "uni", "universitaet", "universität", "student", "studium", "kurs"),
        icon("library", "Bibliothek", Icons.Filled.LocalLibrary, "bibliothek", "buecherei", "bücherei", "library", "ausweis"),
        icon("work", "Arbeit", Icons.Filled.Work, "arbeit", "work", "job", "firma"),
        icon("badge", "Ausweis", Icons.Filled.Badge, "ausweis", "badge", "mitarbeiter", "id", "personal"),
        icon("business", "Unternehmen", Icons.Filled.Business, "unternehmen", "business", "buero", "büro", "office"),
        icon("book", "Dokument", Icons.Filled.Book, "dokument", "buch", "book", "unterlagen"),

        // --- Other ---
        icon("person", "Person", Icons.Filled.Person, "person", "mitglied", "member", "profil", "mitgliedschaft"),
        icon("pets", "Tiere", Icons.Filled.Pets, "tier", "haustier", "pets", "hund", "katze", "tierarzt"),
        icon("cake", "Geburtstag", Icons.Filled.Cake, "geburtstag", "cake", "torte", "jubilaeum", "jubiläum"),
        icon("star", "Favorit", Icons.Filled.Star, "favorit", "star", "stern", "premium", "gold"),
    )

    private val byKey: Map<String, PassIcon> = all.associateBy { it.key }

    /** Resolves a stored key; unknown keys yield `null`. */
    fun byKey(key: String?): PassIcon? = key?.let { byKey[it] }

    /**
     * Free-text search over name and keywords.
     *
     * Matches in the name come before matches in the keywords, prefix matches before partial
     * matches - so "bus" yields the bus and not the mall ("business").
     */
    fun search(query: String): List<PassIcon> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return all

        return all
            .mapNotNull { icon -> icon.rank(needle)?.let { icon to it } }
            .sortedWith(compareBy({ it.second }, { it.first.label }))
            .map { it.first }
    }

    /**
     * Suggests a matching symbol based on free text - e.g. a train symbol for a train ticket.
     *
     * Deliberately simple text matching instead of a classification: the inputs are short and
     * usually contain exactly one telling word. The suggestion is also just a pre-fill that can
     * be changed at any time in the form.
     *
     * Weighted by word length, so a match on "bahn" beats one on "bar" when both occur in the
     * text.
     */
    fun suggestFor(vararg texts: String?): PassIcon? {
        val haystack = texts.filterNotNull().joinToString(separator = " ").lowercase()
        if (haystack.isBlank()) return null

        return all
            .mapNotNull { icon ->
                val best = icon.keywords
                    .filter { keyword -> haystack.containsWord(keyword) }
                    .maxByOrNull { it.length }
                best?.let { icon to it.length }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Smaller values are better matches. */
    private fun PassIcon.rank(needle: String): Int? {
        val name = label.lowercase()
        return when {
            name == needle -> 0
            name.startsWith(needle) -> 1
            keywords.any { it == needle } -> 2
            keywords.any { it.startsWith(needle) } -> 3
            name.contains(needle) -> 4
            keywords.any { it.contains(needle) } -> 5
            else -> null
        }
    }

    /**
     * Checks for a whole word instead of a substring.
     *
     * Without this boundary, "bar" would match inside "Barcelona" or "Bargeld" and put a
     * cocktail glass on a train ticket.
     */
    private fun String.containsWord(word: String): Boolean {
        var from = 0
        while (true) {
            val start = indexOf(word, from)
            if (start < 0) return false
            val end = start + word.length
            val leftFree = start == 0 || !this[start - 1].isLetterOrDigit()
            val rightFree = end == length || !this[end].isLetterOrDigit()
            if (leftFree && rightFree) return true
            from = start + 1
        }
    }

    private fun icon(
        key: String,
        label: String,
        image: ImageVector,
        vararg keywords: String,
    ) = PassIcon(key = key, label = label, keywords = keywords.toList(), image = image)
}
