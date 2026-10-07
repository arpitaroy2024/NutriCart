# Phase 13: Realistic Quantities and Packs

Written 2026-10-07. Covers one change: what a household needs is worked out as a physical amount and then bought in whole packs, and most products gained a smaller pack. Budget arithmetic, nutrition formulas, allergy and health-condition logic, planning periods and the AI features are not changed.

## 1. The model

- One `CatalogItemEntity` row is one purchasable pack.
- `ListItemEntity.quantity` is a whole number of packs. No decimals are stored.
- `gramsPerUnit` is the real weight of one pack. Nutrition is still `quantity x gramsPerUnit`.
- `RegionPriceEntity.price` is whole Tk per pack. A line still costs `quantity x unitPrice`.
- `unit` is the pack's label, used with its price: "kg", "L", "pcs", "250 g", "500 ml".

Three columns were added to `catalog_items`:

| Column | Meaning |
|---|---|
| `packAmount` | The pack's size in its own measure: 1000, 500, 250, 100, or 1 for a piece |
| `packMeasure` | `Gram`, `Millilitre` or `Piece` (`data/model/PackMeasure`) |
| `offered` | False for a pack a smaller one replaced: kept for lists that hold it, no longer offered |

`packAmount` is separate from `gramsPerUnit` because they differ for liquids: a 500 ml bottle of oil is 460 g.

## 2. Room: version 4 to 5

    ALTER TABLE catalog_items ADD COLUMN packAmount  INTEGER NOT NULL DEFAULT 1000
    ALTER TABLE catalog_items ADD COLUMN packMeasure TEXT    NOT NULL DEFAULT 'Gram'
    ALTER TABLE catalog_items ADD COLUMN offered     INTEGER NOT NULL DEFAULT 1
    UPDATE catalog_items SET packMeasure = 'Millilitre' WHERE unit = 'L'
    UPDATE catalog_items SET packAmount = 1, packMeasure = 'Piece' WHERE unit = 'pcs'

The migration adds or removes no row and touches no list. The smaller packs are data, added by `LocalCatalogRepository` the first time the catalog is read: rows the database lacks are inserted with `INSERT OR IGNORE`, and the rows they replace are marked not offered. A row that is already there is never rewritten, so its unit, weight and prices stay as they were.

## 3. Packs added (demo data)

Every product sold by the kilo or the litre has one smaller pack: 38 new rows, id = original id + 100. The 40 original rows are unchanged; the 38 that were replaced are no longer offered. Eggs and bananas are sold by the piece already. Forty packs are offered, one per product.

| Pack | Products |
|---|---|
| 500 g | Brown rice, white rice, atta, potato, pumpkin, cauliflower, bottle gourd, papaya, sugar |
| 250 g | Oats, lentils, mung dal, chickpeas, peanuts, chicken, beef, rui, hilsa, small fish, shrimp, chicken liver, spinach, red amaranth, onion, tomato, eggplant, carrot, guava, yogurt, jaggery, chanachur |
| 100 g | Dried fish |
| 500 ml | Milk, soybean oil, rice bran oil, sunflower oil |
| 250 ml | Mustard oil, peanut oil |

A pack's base price is the same share of the original's, rounded up to a whole taka (Tk 160 a kilo of lentils, Tk 40 for 250 g; Tk 75 a kilo of rice, Tk 38 for 500 g). Regional prices use the existing percentages. A pack's weight is the same share of the original's. None of these are market prices.

## 4. Generation

`BasketSlot.amountPerPerson` replaces `perPerson`: what one person needs in a month as a physical amount, in grams, millilitres or pieces. The figures are the old ones multiplied out (6 kg of rice is 6000 g), and each slot names the packs now offered.

    required = amountPerPerson x effectivePeople(household) x days / 30     (30 days: not scaled)
    packs    = ceil(required / item.packAmount), at least 1

The amount is scaled to the period first and turned into packs last. Rounding is up, so a list is never short: 250 g in 100 g packs is three packs. Everything after that is unchanged: priority order, proportional scaling of staples, the reserve, upgrades, extras, filtering. A staple is upgraded only to a pack of the same size.

For one person for a week the list now has 250 g of lentils (was 1 kg), 500 ml of oil (was 1 L) and 1.5 kg of rice (was 1 kg, which was short of the 1.4 kg needed).

**Effect on a month's list.** Amounts are the same wherever the old target was a whole number of packs: four people still get 24 kg of rice, 4 kg of lentils, 48 eggs. Where the old code rounded to the nearest kilo, the new code rounds up to the next pack. Pack prices rounded up to whole taka make some totals a few taka higher.

## 5. Display

`ui/Formatting.formatAmount(amount, measure)` is the one place an amount is put into words: "250 g", "1 kg", "1.5 kg", "500 ml", "1.5 L", "6 pcs". `amountLabel()` gives the total for a number of packs.

| Screen | Shows |
|---|---|
| Grocery list | The total amount of each line |
| Edit list | The total amount between minus and plus; the price as "Tk 43 per 250 g" |
| Item picker | "In your list: 750 g"; the price per pack |
| Review alternatives | The total amount and cost |

In the editor a step is one pack of that item: 1.5 kg of rice in half-kilo packs becomes 1 kg, 300 g in 100 g packs becomes 200 g, and a kilo pack from an older list steps by the kilo. Minus stops at one pack and removing an item is the existing remove action.

## 6. Older lists and duplicates

- A list made before this phase keeps its rows and reads as before ("24 kg").
- The item picker shows the packs offered now. If the list holds a withdrawn pack, that pack is shown so more can be added, and its replacement is left out, so no food is listed twice.
- Suggested alternatives come from offered packs only, and never another pack of a food already on the list.

## 7. AI context

Each item gained `amount`, the total in words ("750 g"). `quantity` and `unit` are still the pack count and the pack. Nothing else in the AI layer changed.

## 8. Tests

`ui/PackQuantityTest` (16): the formatter; pack rounding; scaling before conversion; one person for a week; the monthly amounts; generation through the screen; stepping by packs of 100 g, 500 g, a kilo and pieces; the minimum and removal; price; nutrition equal for the same weight in any pack; seed consistency; topping up an older catalog; an older list opening, editing and adding; the picker; the AI amount.

`data/local/MigrationTest` (+2): version 4 to 5 with catalog rows, a list and items; an empty catalog.

Adjusted for the new pack ids and amounts: the generator, generation, list-editing, review, navigation, planning-period, catalog-seeding and conflict-engine tests.

## 9. Device check (2026-10-07)

On a physical phone holding data from earlier versions: the upgrade ran; the older list still opened and read "1 kg"; a one-week list for one person came out with 1.5 kg of rice, 250 g of lentils and 500 g of potato; in the editor minus took the rice from 1.5 kg to 1 kg, the line from Tk 180 to Tk 120 and the total from Tk 1,378 to Tk 1,318. Nutrition and the AI card were not checked on the device in this phase.

## 10. Known limitations

1. One pack size per product. The generator does not mix sizes, so a large household's rice is counted in half kilos.
2. Rounding up over-buys by up to one pack per line.
3. Pack prices are rounded up to whole taka, so a pack costs slightly more per gram than the kilo did.
4. A list that holds a withdrawn kilo pack cannot be given the smaller pack of the same food from the picker; remove the item and add it again.
5. `unit` labels and amounts are English symbols.
6. The catalog top-up runs once per app start and only adds; it never corrects a row that differs from the seed.
