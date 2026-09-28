#!/usr/bin/env python3
"""Generates announce_data_en.sql (fresh seed) and announce_reseed_en.sql in this directory
(in-place migration of a live database still holding the litemall demo content).
Run from the repository root: python3 db-reactive/sql/gen_seed_en.py .
Edit the tables in this file and regenerate; never hand-edit the two SQL files."""
import re, sys, pathlib

ROOT = pathlib.Path(sys.argv[1])
SQL = ROOT / "db-reactive/sql"
OLD = (SQL / "announce_data_en.sql").read_text(encoding="utf-8")
T = "2026-09-28 00:00:00"

def keep(table):
    m = re.search(r"^INSERT INTO `%s` .*?;$" % table, OLD, re.M | re.S)
    if not m:
        sys.exit("missing %s in old seed" % table)
    return m.group(0)

ADMIN, PERMISSION, USER = keep("announce_admin"), keep("announce_permission"), keep("announce_user")

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

def row(*vals):
    return "(" + ",".join(q(v) for v in vals) + ")"

# ---------------------------------------------------------------- roles / keywords / brands
ROLES = [(1, "Super administrator", "All modules"),
         (2, "Marketplace administrator", "Announces, categories, brands and crawled listings"),
         (3, "Support administrator", "Support conversations and bulletins")]
KEYWORDS = [(1, "bicycle", 1, 0, 1), (2, "sofa", 1, 0, 2), (3, "iPhone", 1, 0, 3), (4, "Volvo", 0, 0, 4),
            (5, "pram", 0, 0, 5), (6, "concert tickets", 0, 1, 6), (7, "apartment", 0, 0, 7)]
BRANDS = [(1001000, "Apple", "Phones, tablets and computers"), (1001001, "Samsung", "Phones, TVs and appliances"),
          (1001002, "IKEA", "Furniture and home"), (1001003, "Bosch", "Appliances and tools"),
          (1001004, "Trek", "Bicycles"), (1001005, "Volkswagen", "Cars"), (1001006, "Toyota", "Cars"),
          (1001007, "Sony", "TV, audio and cameras"), (1001008, "Nike", "Sportswear and shoes"),
          (1001009, "Fjällräven", "Outdoor clothing and backpacks"), (1001010, "Honda", "Cars and motorcycles"),
          (1001011, "Lenovo", "Computers")]

# ---------------------------------------------------------------- categories (id, name, desc, pid, kind)
CATS = []
def root(id_, name, desc, kind, children):
    CATS.append((id_, name, desc, 0, "L1", kind))
    for i, (cid, cname, cdesc) in enumerate(children, 1):
        CATS.append((cid, cname, cdesc, id_, "L2", kind))

root(1005000, "Vehicles", "Cars, bikes, boats and parts", "GOODS", [
    (1005001, "Cars", "Private cars"), (1005002, "Motorcycles", "Motorcycles and scooters"),
    (1005003, "Bicycles", "City, road and mountain bikes"), (1005004, "Boats", "Boats and engines"),
    (1005005, "Campers and trailers", "Caravans, campers, trailers"), (1005006, "Car parts and accessories", "Tyres, rims, parts")])
root(1006000, "Home and garden", "Furniture, kitchen, garden, tools", "GOODS", [
    (1006001, "Furniture", "Sofas, tables, beds, storage"), (1006002, "Kitchen and dining", "Appliances, cookware, tableware"),
    (1006003, "Garden and outdoor", "Plants, garden furniture, paving"), (1006004, "Lighting and decoration", "Lamps, art, textiles"),
    (1006005, "Tools and DIY", "Power tools, hand tools, materials")])
root(1007000, "Electronics", "Phones, computers, TV and audio", "GOODS", [
    (1007001, "Phones and tablets", "Smartphones, tablets, wearables"), (1007002, "Computers", "Laptops, desktops, parts"),
    (1007003, "TV and audio", "Televisions, speakers, hi-fi"), (1007004, "Cameras", "Cameras, lenses, drones"),
    (1007005, "Gaming", "Consoles and games")])
root(1008000, "Fashion", "Clothing, shoes and accessories", "GOODS", [
    (1008001, "Women's clothing", ""), (1008002, "Men's clothing", ""), (1008003, "Shoes", ""),
    (1008004, "Bags and accessories", ""), (1008005, "Watches and jewellery", "")])
root(1009000, "Kids and baby", "Everything for children", "GOODS", [
    (1009001, "Prams and car seats", ""), (1009002, "Toys", ""), (1009003, "Children's clothing", ""),
    (1009004, "Nursery furniture", "")])
root(1010000, "Sports and leisure", "Fitness, outdoor, music", "GOODS", [
    (1010001, "Fitness", "Training equipment"), (1010002, "Outdoor and camping", "Tents, backpacks, hiking"),
    (1010003, "Winter sports", "Skis, snowboards, skates"), (1010004, "Water sports", "Kayaks, surf, diving"),
    (1010005, "Musical instruments", "Guitars, pianos, drums")])
root(1011000, "Hobbies and collectibles", "Books, art, collections", "GOODS", [
    (1011001, "Books and comics", ""), (1011002, "Art and antiques", ""), (1011003, "Stamps and coins", ""),
    (1011004, "Board games and models", "")])
root(1012000, "Animals and pet supplies", "Pets and accessories", "GOODS", [
    (1012001, "Dogs", ""), (1012002, "Cats", ""), (1012003, "Horses", ""), (1012004, "Pet accessories", "")])
root(1013000, "Other", "Everything else", "GOODS", [(1013001, "Miscellaneous", "Items that fit nowhere else")])
root(1037000, "Jobs", "Find your next job", "JOB", [
    (1037001, "IT and software", "Developers, ops, data"), (1037002, "Healthcare", "Nurses, doctors, care"),
    (1037003, "Hospitality", "Restaurants, hotels, tourism"), (1037004, "Trades and construction", "Electricians, carpenters, builders"),
    (1037005, "Retail and sales", "Shops and sales"), (1037006, "Education", "Teachers and tutors"),
    (1037007, "Transport and logistics", "Drivers and warehouse"), (1037008, "Office and administration", "Admin, finance, HR")])
root(1038000, "Events", "What is on near you", "EVENT", [
    (1038001, "Concerts", "Live music"), (1038002, "Sports", "Matches, races, tournaments"),
    (1038003, "Markets and fairs", "Flea markets, fairs, festivals"), (1038004, "Theatre and film", "Stage and screen"),
    (1038005, "Courses and workshops", "Learn something new"), (1038006, "Family and kids", "For the whole family"),
    (1038007, "Community and clubs", "Meetups and club life")])

# ---------------------------------------------------------------- regions: type 1 country, 2 region/county, 3 municipality
REGIONS = []   # (id, pid, name, type, code)
BY_NAME = {}
def region(id_, pid, name, type_, code):
    REGIONS.append((id_, pid, name, type_, code))
    if type_ == 3 or name not in BY_NAME:
        BY_NAME[name] = id_

region(1, 0, "Denmark", 1, 208)
DK = {
    (2, "Hovedstaden", 1084): [(101, "København"), (147, "Frederiksberg"), (151, "Ballerup"), (153, "Brøndby"), (155, "Dragør"),
        (157, "Gentofte"), (159, "Gladsaxe"), (161, "Glostrup"), (163, "Herlev"), (165, "Albertslund"), (167, "Hvidovre"),
        (169, "Høje-Taastrup"), (173, "Lyngby-Taarbæk"), (175, "Rødovre"), (183, "Ishøj"), (185, "Tårnby"), (187, "Vallensbæk"),
        (190, "Furesø"), (201, "Allerød"), (210, "Fredensborg"), (217, "Helsingør"), (219, "Hillerød"), (223, "Hørsholm"),
        (230, "Rudersdal"), (240, "Egedal"), (250, "Frederikssund"), (260, "Halsnæs"), (270, "Gribskov"), (400, "Bornholm")],
    (3, "Sjælland", 1085): [(253, "Greve"), (259, "Køge"), (265, "Roskilde"), (269, "Solrød"), (306, "Odsherred"), (316, "Holbæk"),
        (320, "Faxe"), (326, "Kalundborg"), (329, "Ringsted"), (330, "Slagelse"), (336, "Stevns"), (340, "Sorø"), (350, "Lejre"),
        (360, "Lolland"), (370, "Næstved"), (376, "Guldborgsund"), (390, "Vordingborg")],
    (4, "Syddanmark", 1083): [(410, "Middelfart"), (420, "Assens"), (430, "Faaborg-Midtfyn"), (440, "Kerteminde"), (450, "Nyborg"),
        (461, "Odense"), (479, "Svendborg"), (480, "Nordfyn"), (482, "Langeland"), (492, "Ærø"), (510, "Haderslev"), (530, "Billund"),
        (540, "Sønderborg"), (550, "Tønder"), (561, "Esbjerg"), (563, "Fanø"), (573, "Varde"), (575, "Vejen"), (580, "Aabenraa"),
        (607, "Fredericia"), (621, "Kolding"), (630, "Vejle")],
    (5, "Midtjylland", 1082): [(615, "Horsens"), (657, "Herning"), (661, "Holstebro"), (665, "Lemvig"), (671, "Struer"), (706, "Syddjurs"),
        (707, "Norddjurs"), (710, "Favrskov"), (727, "Odder"), (730, "Randers"), (740, "Silkeborg"), (741, "Samsø"), (746, "Skanderborg"),
        (751, "Aarhus"), (756, "Ikast-Brande"), (760, "Ringkøbing-Skjern"), (766, "Hedensted"), (779, "Skive"), (791, "Viborg")],
    (6, "Nordjylland", 1081): [(773, "Morsø"), (787, "Thisted"), (810, "Brønderslev"), (813, "Frederikshavn"), (820, "Vesthimmerland"),
        (825, "Læsø"), (840, "Rebild"), (846, "Mariagerfjord"), (849, "Jammerbugt"), (851, "Aalborg"), (860, "Hjørring")],
}
next_id = 100
for (rid, rname, rcode), munis in DK.items():
    region(rid, 1, rname, 2, rcode)
    for code, name in munis:
        region(next_id, rid, name, 3, code); next_id += 1
assert next_id - 100 == 98, next_id

region(200, 0, "Sweden", 1, 752)
SE = [(1, "Stockholm", [(180, "Stockholm"), (184, "Solna"), (182, "Nacka"), (126, "Huddinge"), (181, "Södertälje"), (127, "Botkyrka"),
                        (136, "Haninge"), (160, "Täby"), (163, "Sollentuna")]),
      (3, "Uppsala", [(380, "Uppsala"), (381, "Enköping")]),
      (4, "Södermanland", [(484, "Eskilstuna"), (480, "Nyköping")]),
      (5, "Östergötland", [(580, "Linköping"), (581, "Norrköping")]),
      (6, "Jönköping", [(680, "Jönköping"), (683, "Värnamo")]),
      (7, "Kronoberg", [(780, "Växjö")]),
      (8, "Kalmar", [(880, "Kalmar"), (883, "Västervik")]),
      (9, "Gotland", [(980, "Visby")]),
      (10, "Blekinge", [(1080, "Karlskrona")]),
      (12, "Skåne", [(1280, "Malmö"), (1283, "Helsingborg"), (1281, "Lund"), (1290, "Kristianstad"), (1282, "Landskrona")]),
      (13, "Halland", [(1380, "Halmstad"), (1383, "Varberg"), (1384, "Kungsbacka")]),
      (14, "Västra Götaland", [(1480, "Göteborg"), (1490, "Borås"), (1481, "Mölndal"), (1488, "Trollhättan"), (1485, "Uddevalla"), (1496, "Skövde")]),
      (17, "Värmland", [(1780, "Karlstad")]),
      (18, "Örebro", [(1880, "Örebro")]),
      (19, "Västmanland", [(1980, "Västerås")]),
      (20, "Dalarna", [(2080, "Falun"), (2081, "Borlänge")]),
      (21, "Gävleborg", [(2180, "Gävle")]),
      (22, "Västernorrland", [(2281, "Sundsvall"), (2284, "Örnsköldsvik")]),
      (23, "Jämtland", [(2380, "Östersund")]),
      (24, "Västerbotten", [(2480, "Umeå"), (2482, "Skellefteå")]),
      (25, "Norrbotten", [(2580, "Luleå"), (2584, "Kiruna")])]
lan_id, muni_id = 201, 300
for code, name, munis in SE:
    region(lan_id, 200, name, 2, code)
    for mcode, mname in munis:
        region(muni_id, lan_id, mname, 3, mcode); muni_id += 1
    lan_id += 1
assert lan_id == 222

# ---------------------------------------------------------------- sample announces (seller 1 = user123)
PUB, EXP = "2026-09-20 10:00:00", "2027-03-20 10:00:00"
def pic(id_, n=2):
    return "[" + ", ".join('"https://picsum.photos/seed/announce-%s-%d/800/600"' % (id_, i) for i in range(1, n + 1)) + "]"

# (id, category, title, brief, detail, city, postal, region name, keywords)
GOODS = [
    ("2000001", 1005001, "Volkswagen Golf 1.5 TSI 2019", "One owner, full service history", "<p>Golf 1.5 TSI Comfortline from 2019, 68,000 km, petrol, automatic. Serviced at the dealer, two sets of tyres on rims, no known faults.</p>", "København", "2100", "København", "golf,vw,car",
     149900.00, "GOOD", "FIXED_PRICE", 1001005, [("km", "68000"), ("model_year", "2019"), ("fuel", "Petrol")]),
    ("2000002", 1005001, "Toyota Yaris Hybrid 2021", "Low mileage, winter tyres included", "<p>Yaris 1.5 Hybrid Active, 31,000 km, one owner, non-smoker. Winter tyres on rims included.</p>", "Aarhus", "8000", "Aarhus", "yaris,toyota,hybrid",
     169000.00, "LIKE_NEW", "FIXED_PRICE", 1001006, [("km", "31000"), ("model_year", "2021"), ("fuel", "Hybrid")]),
    ("2000003", 1005003, "Trek FX 3 hybrid bike, size M", "Serviced this spring", "<p>Trek FX 3 Disc, frame size M (54 cm), hydraulic disc brakes, new chain and cassette in April. Comes with rack and mudguards.</p>", "Odense", "5000", "Odense", "bicycle,trek",
     3200.00, "GOOD", "FIXED_PRICE", 1001004, [("frame_size", "M"), ("gears", "27")]),
    ("2000004", 1005002, "Honda CB500F 2018", "Two keys, new chain kit", "<p>Honda CB500F, 21,000 km, A2 compliant. New chain kit and rear tyre this season, always garaged.</p>", "Kolding", "6000", "Kolding", "honda,motorcycle",
     39900.00, "GOOD", "FIXED_PRICE", 1001010, [("km", "21000"), ("model_year", "2018")]),
    ("2000005", 1005004, "Yamarin 46 SC with 40 hp Yamaha", "Trailer included, ready for the water", "<p>Yamarin 46 Side Console from 2012 with a Yamaha F40 four-stroke. Brenderup trailer, cover and fish finder included. Sold to the highest bidder.</p>", "Helsingør", "3000", "Helsingør", "boat,yamarin,yamaha",
     89000.00, "GOOD", "AUCTION", None, [("model_year", "2012"), ("engine_hp", "40")]),
    ("2000006", 1006001, "IKEA Kivik three-seat sofa", "Grey cover, no stains", "<p>Kivik three-seat sofa in grey, three years old, from a pet-free and smoke-free home. Cover washed, no stains or tears. Collection only.</p>", "Aalborg", "9000", "Aalborg", "sofa,ikea",
     1500.00, "GOOD", "FIXED_PRICE", 1001002, []),
    ("2000007", 1006002, "Bosch dishwasher, 60 cm", "Two years old, works perfectly", "<p>Bosch Serie 4 built-under dishwasher, 60 cm, silver. Two years old and replaced only because of a kitchen renovation.</p>", "Esbjerg", "6700", "Esbjerg", "dishwasher,bosch",
     1800.00, "GOOD", "FIXED_PRICE", 1001003, [("width_cm", "60")]),
    ("2000008", 1006003, "Free garden paving stones", "About 12 m2, you collect", "<p>Grey concrete paving stones, 30 x 30 cm, about 12 m2 stacked on pallets. Free to whoever collects them this month.</p>", "Vejle", "7100", "Vejle", "paving,garden,free",
     0.00, "FAIR", "GIVEAWAY", None, []),
    ("2000009", 1007001, "iPhone 14 128 GB, midnight", "Battery health 91%, with case", "<p>iPhone 14 128 GB in midnight, bought in 2023. Battery health 91%, no scratches, silicone case and original box included.</p>", "København", "1050", "København", "iphone,apple",
     3900.00, "GOOD", "FIXED_PRICE", 1001000, [("storage", "128 GB"), ("colour", "Midnight")]),
    ("2000010", 1007002, "Lenovo ThinkPad T14 Gen 3", "16 GB RAM, 512 GB SSD", "<p>ThinkPad T14 Gen 3 with Ryzen 5 Pro, 16 GB RAM and 512 GB SSD. Swedish keyboard, Windows 11 Pro, charger included.</p>", "Stockholm", "111 22", "Stockholm", "laptop,thinkpad,lenovo",
     6500.00, "LIKE_NEW", "FIXED_PRICE", 1001011, [("ram", "16 GB"), ("storage", "512 GB")]),
    ("2000011", 1007003, "Sony Bravia 55-inch 4K TV", "Wall bracket included", "<p>Sony KD-55X85K, 55 inch 4K LED, bought in 2023. Wall bracket and remote included, picture perfect.</p>", "Göteborg", "411 03", "Göteborg", "tv,sony,4k",
     3500.00, "GOOD", "FIXED_PRICE", 1001007, [("screen_size", "55 inch")]),
    ("2000012", 1008003, "Nike Pegasus 40, size 43", "Worn twice", "<p>Nike Air Zoom Pegasus 40 in black, EU 43. Worn twice indoors, wrong size for me.</p>", "Malmö", "211 20", "Malmö", "nike,running,shoes",
     450.00, "LIKE_NEW", "FIXED_PRICE", 1001008, [("size", "43")]),
    ("2000013", 1009001, "Bugaboo Fox 3 pram", "Complete set with carrycot", "<p>Bugaboo Fox 3 in black with carrycot, seat, rain cover and sun canopy. Used for one child.</p>", "Roskilde", "4000", "Roskilde", "pram,bugaboo",
     4500.00, "GOOD", "FIXED_PRICE", None, []),
    ("2000014", 1010002, "Fjällräven Kajka 65 backpack", "Used on one hike", "<p>Kajka 65 in forest green, size S/M. Used on a single week-long hike, no wear.</p>", "Uppsala", "753 10", "Uppsala", "backpack,fjallraven,hiking",
     1900.00, "LIKE_NEW", "FIXED_PRICE", 1001009, [("volume", "65 l")]),
    ("2000015", 1011001, "Harry Potter box set, hardcover", "All seven volumes", "<p>Complete hardcover box set of the seven Harry Potter books in English. Read once, spines intact.</p>", "Lund", "222 22", "Lund", "books,harry potter",
     350.00, "GOOD", "FIXED_PRICE", None, []),
]
JOBS = [
    ("2000016", 1037001, "Backend developer (Java, Spring)", "Hybrid, Copenhagen", "<p>We are a 30-person SaaS company building logistics software. You will work on our Spring Boot services, Kafka pipelines and MySQL read models. Three days a week in the office at Islands Brygge.</p>", "København", "2300", "København", "java,spring,developer",
     "Nordic Cloud ApS", "FULL_TIME", 45000.00, 55000.00, "DKK", "2026-11-15 23:59:00", "jobs@nordiccloud.invalid"),
    ("2000017", 1037004, "Electrician for housing projects", "Start as soon as possible", "<p>Certified electrician for new-build housing projects around Aarhus. Company van, tools and pension. Danish or English spoken.</p>", "Aarhus", "8200", "Aarhus", "electrician,construction",
     "Jysk El A/S", "FULL_TIME", 34000.00, 40000.00, "DKK", "2026-10-31 23:59:00", "https://jysk-el.invalid/jobs/electrician"),
    ("2000018", 1037003, "Weekend barista", "Saturdays and Sundays, 8-16", "<p>Small specialty coffee bar in Möllevången looks for a weekend barista. Experience with espresso machines is a plus, a good mood is a must.</p>", "Malmö", "214 20", "Malmö", "barista,cafe,part-time",
     "Kaffebaren AB", "PART_TIME", 22000.00, 25000.00, "SEK", "2026-10-20 23:59:00", "hello@kaffebaren.invalid"),
]
EVENTS = [
    ("2000019", 1038001, "Autumn jazz night at Musikhuset", "Three bands, one evening", "<p>The Aarhus Jazz Club presents three Nordic bands in the small hall of Musikhuset. Doors open at 19:30, bar and food available.</p>", "Aarhus", "8000", "Aarhus", "jazz,concert",
     "2026-11-14 20:00:00", "2026-11-14 23:00:00", "Musikhuset Aarhus, Thomas Jensens Allé 2", "Aarhus Jazz Club", 1, 180.00, 400),
    ("2000020", 1038003, "Christmas flea market in Kolding", "Sixty stalls, free entry", "<p>Second-hand toys, clothes, furniture and Christmas decorations. Bring cash and a bag. Stalls can be booked through the organiser.</p>", "Kolding", "6000", "Kolding", "flea market,christmas",
     "2026-12-06 10:00:00", "2026-12-06 16:00:00", "Kolding Storcenter, Skovvangen 42", "Kolding Loppemarked", 0, None, None),
    ("2000021", 1038005, "Beginner's sourdough workshop", "Bake and take your loaf home", "<p>Four hours on starters, folding and baking. All ingredients included, and you leave with a loaf and a jar of starter. Twelve places.</p>", "Stockholm", "118 20", "Stockholm", "sourdough,baking,workshop",
     "2026-11-22 13:00:00", "2026-11-22 17:00:00", "Södermalm Kitchen Studio, Hornsgatan 45", "Bröd & Co", 1, 450.00, 12),
]

ANN, GD, JD, ED, ATTR = [], [], [], [], []
attr_id = 1
def announce(id_, kind, cat, title, brief, detail, city, postal, regname, kw, expires):
    ANN.append(row(id_, kind, 1, cat, title, brief, detail, pic(id_), kw, BY_NAME[regname], city, postal, None, None, "PUBLISHED", PUB, expires, None, T, T, 0))
for (id_, cat, title, brief, detail, city, postal, regname, kw, price, cond, sale, brand, attrs) in GOODS:
    announce(id_, "GOODS", cat, title, brief, detail, city, postal, regname, kw, EXP)
    GD.append(row(id_, price, cond, sale, brand, "pcs"))
    for a, v in attrs:
        ATTR.append(row(attr_id, id_, a, v, T, T, 0)); attr_id += 1
for (id_, cat, title, brief, detail, city, postal, regname, kw, employer, ctype, smin, smax, cur, deadline, apply) in JOBS:
    announce(id_, "JOB", cat, title, brief, detail, city, postal, regname, kw, deadline)
    JD.append(row(id_, employer, ctype, smin, smax, cur, deadline, apply))
for (id_, cat, title, brief, detail, city, postal, regname, kw, starts, ends, venue, org, ticketed, tprice, cap) in EVENTS:
    announce(id_, "EVENT", cat, title, brief, detail, city, postal, regname, kw, ends)
    ED.append(row(id_, starts, ends, venue, org, ticketed, tprice, cap))

# ---------------------------------------------------------------- statements
INSERTS = {
    "announce_role": "INSERT INTO `announce_role` VALUES " + ",".join(row(i, n, d, 1, T, T, 0) for i, n, d in ROLES) + ";",
    "announce_keyword": "INSERT INTO `announce_keyword` VALUES " + ",".join(row(i, k, "", h, d, s, T, T, 0) for i, k, h, d, s in KEYWORDS) + ";",
    "announce_brand": "INSERT INTO `announce_brand` VALUES " + ",".join(row(i, n, d, "", 50, 0.00, T, T, 0) for i, n, d in BRANDS) + ";",
    "announce_category": "INSERT INTO `announce_category` (`id`, `name`, `keywords`, `desc`, `pid`, `icon_url`, `pic_url`, `level`, `sort_order`, `add_time`, `update_time`, `deleted`, `kind`) VALUES "
                         + ",\n".join(row(i, n, "", d, p, "", "", lv, (idx % 100) + 1, T, T, 0, k) for idx, (i, n, d, p, lv, k) in enumerate(CATS)) + ";",
    "announce_region": "INSERT INTO `announce_region` VALUES " + ",\n".join(row(*r) for r in REGIONS) + ";",
    "announce": "INSERT INTO `announce` (`id`, `kind`, `seller_id`, `category_id`, `title`, `brief`, `detail`, `gallery`, `keywords`, `region_id`, `city`, `postal_code`, `latitude`, `longitude`, `status`, `published_at`, `expires_at`, `last_event_id`, `add_time`, `update_time`, `deleted`) VALUES\n"
                + ",\n".join(ANN) + ";",
    "announce_goods_detail": "INSERT INTO `announce_goods_detail` (`announce_id`, `price`, `condition`, `sale_type`, `brand_id`, `unit`) VALUES " + ",".join(GD) + ";",
    "announce_job": "INSERT INTO `announce_job` (`announce_id`, `employer`, `contract_type`, `salary_min`, `salary_max`, `currency`, `application_deadline`, `apply_url_or_email`) VALUES " + ",\n".join(JD) + ";",
    "announce_event": "INSERT INTO `announce_event` (`announce_id`, `starts_at`, `ends_at`, `venue`, `organizer`, `ticketed`, `ticket_price`, `capacity`) VALUES " + ",\n".join(ED) + ";",
    "announce_goods_attribute": "INSERT INTO `announce_goods_attribute` (`id`, `announce_id`, `attribute`, `value`, `add_time`, `update_time`, `deleted`) VALUES " + ",".join(ATTR) + ";",
}

def section(table, insert):
    return ("--\n-- Dumping data for table `%s`\n--\n\nLOCK TABLES `%s` WRITE;\n/*!40000 ALTER TABLE `%s` DISABLE KEYS */;\n%s\n/*!40000 ALTER TABLE `%s` ENABLE KEYS */;\nUNLOCK TABLES;\n\n" % (table, table, table, insert, table))

HEAD = """--
-- announce_reactive_db development seed (English).
-- Generated by gen_seed_en.py in this directory; edit that script and regenerate, never this file.
-- Contents: one administrator and one member (both litemall defaults), roles and permissions,
-- the category trees of the three kinds (GOODS, JOB, EVENT), a few brands and keywords,
-- regions of Denmark (country, 5 regions, 98 municipalities) and Sweden (country, 21 counties,
-- main municipalities), and 21 published sample announces owned by member 1 (user123).
-- No litemall demo content remains: ads, topics, comments, issues and footprints are empty.
-- ------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

"""
FOOT = """/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
"""

seed = HEAD
seed += section("announce_admin", ADMIN) + section("announce_permission", PERMISSION) + section("announce_user", USER)
for t in ["announce_role", "announce_keyword", "announce_brand", "announce_category", "announce_region",
          "announce", "announce_goods_detail", "announce_job", "announce_event", "announce_goods_attribute"]:
    seed += section(t, INSERTS[t])
seed += FOOT
(SQL / "announce_data_en.sql").write_text(seed, encoding="utf-8")

# ---------------------------------------------------------------- in-place migration for a live database
mig = """-- Replaces the litemall demo content of a live announce_reactive_db by the English classifieds seed.
-- Keeps: members, administrators, permissions, refresh tokens, crawled listings, orders, conversations,
-- and every announce posted through the application (ids are UUIDs). Removes the 7-digit litemall goods
-- and their children, and reloads categories, brands, keywords, regions and the sample announces.
-- Run with: db-reactive/sql/reseed_en.sh (takes a backup first, then cleans the search index).
-- Generated by gen_seed_en.py in this directory together with announce_data_en.sql.

/*!40101 SET NAMES utf8mb4 */;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;

-- 1. litemall goods (7-digit ids) and every child row keyed by them
DELETE FROM `announce_goods_attribute` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce_goods_product` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce_goods_specification` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce_goods_detail` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce_job` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce_event` WHERE `announce_id` REGEXP '^[0-9]{7}$';
DELETE FROM `announce` WHERE `id` REGEXP '^[0-9]{7}$';

-- 2. legacy litemall tables that nothing reads any more
DELETE FROM `announce_ad`;
DELETE FROM `announce_topic`;
DELETE FROM `announce_comment`;
DELETE FROM `announce_issue`;
DELETE FROM `announce_log`;
DELETE FROM `announce_footprint`;
DELETE FROM `announce_system`;

-- 3. reference data
DELETE FROM `announce_keyword`;
DELETE FROM `announce_brand`;
DELETE FROM `announce_category`;
DELETE FROM `announce_region`;
UPDATE `announce_role` SET `name`='Super administrator', `desc`='All modules', `update_time`=NOW() WHERE `id`=1;
UPDATE `announce_role` SET `name`='Marketplace administrator', `desc`='Announces, categories, brands and crawled listings', `update_time`=NOW() WHERE `id`=2;
UPDATE `announce_role` SET `name`='Support administrator', `desc`='Support conversations and bulletins', `update_time`=NOW() WHERE `id`=3;
%s
%s
%s
%s

-- 4. sample announces
%s
%s
%s
%s
%s

-- 5. announces posted through the application that pointed at a removed category
UPDATE `announce` SET `category_id`=1005003 WHERE `kind`='GOODS' AND `category_id`=1036000;  -- the checklist bikes
UPDATE `announce` a LEFT JOIN `announce_category` c ON c.`id`=a.`category_id` SET a.`category_id`=1013001 WHERE a.`kind`='GOODS' AND c.`id` IS NULL;
UPDATE `announce` a LEFT JOIN `announce_category` c ON c.`id`=a.`category_id` SET a.`category_id`=1037008 WHERE a.`kind`='JOB' AND c.`id` IS NULL;
UPDATE `announce` a LEFT JOIN `announce_category` c ON c.`id`=a.`category_id` SET a.`category_id`=1038007 WHERE a.`kind`='EVENT' AND c.`id` IS NULL;
UPDATE `announce` a JOIN `announce_region` r ON r.`name`=a.`city` AND r.`type`=3 SET a.`region_id`=r.`id` WHERE a.`region_id` IS NULL;
UPDATE `crawled_listing` l JOIN `announce_region` r ON r.`name`=l.`city` AND r.`type`=3 SET l.`region_id`=r.`id` WHERE l.`region_id` IS NULL;
DELETE FROM `listing_category_map` WHERE `category_id` NOT IN (SELECT `id` FROM `announce_category`);

SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
""" % (INSERTS["announce_keyword"], INSERTS["announce_brand"], INSERTS["announce_category"], INSERTS["announce_region"],
       INSERTS["announce"], INSERTS["announce_goods_detail"], INSERTS["announce_job"], INSERTS["announce_event"], INSERTS["announce_goods_attribute"])
(SQL / "announce_reseed_en.sql").write_text(mig, encoding="utf-8")
print("categories", len(CATS), "regions", len(REGIONS), "announces", len(ANN))
