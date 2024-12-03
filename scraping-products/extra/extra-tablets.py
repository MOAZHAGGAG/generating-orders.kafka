import requests
import json
import time
import csv
from datetime import datetime, timedelta
import psycopg2
from psycopg2 import pool
from psycopg2.extras import execute_batch
from config import DB_CONFIG

# Start time
start_time = time.time()

# Create a connection pool
connection_pool = None
try:
    connection_pool = psycopg2.pool.SimpleConnectionPool(
        minconn=1,
        maxconn=100,
        host=DB_CONFIG["host"],
        port=DB_CONFIG["port"],
        dbname=DB_CONFIG["dbname"],
        user=DB_CONFIG["user"],
        password=DB_CONFIG["password"]
    )
    print("Successfully connected to PostgreSQL.")
except psycopg2.OperationalError as e:
    print(f"Error connecting to PostgreSQL: {e}. Skipping database operations.")

tablename = DB_CONFIG.get("tablename", "test")

# Base API URL
API_URL = "https://search.unbxd.io/21705619e273429e5767eea44ccb1ad5/ss-unbxd-auk-extra-saudi-en-prod11541714990488/category?stats=price&selectedfacet=true&facet.multiselect=true&page=2&rows=96&bfrule=inStockCities%3A%22SA-riyadh%22+OR+sellingOutFastCities%3A%22SA-riyadh%22+OR+restockableCities%3ASA-riyadh&boost=if%28eq%28query%28%24bfrule%29%2Cfalse%29%2C0%2C1%29&filter=familyEn_uFilter%3ATablets&filter=type%3APRODUCT&p=categories_uFilter%3A%222%22&pagetype=boolean&facet=true&version=V2&uid=uid-1726931238891-10989"

# Custom headers
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    'Accept': 'application/json',
    'Accept-Language': 'en-US,en;q=0.9,ar-EG;q=0.8,ar;q=0.7',
    'Cache-Control': 'no-cache',
    'Content-Type': 'application/json',
    'Connection': 'keep-alive',
    'Host': 'search.unbxd.io',
    'Referer': 'https://www.extra.com/en-sa/',
    'Pragma': 'no-cache',
}

def format_brand(brand):
    """
    Format the brand name according to the specified capitalization rules.
    """
    brand_mapping = {
        "TECNO": "Tecno",
        "APPLE": "Apple",
        "NOKIA": "Nokia",
        "XIAOMI": "Xiaomi",
        "VIVO": "Vivo",
        "MOTOROLA": "Motorola",
        "HUAWEI": "Huawei",
        "NOTHING": "Nothing",
        "REALME": "Realme",
        "HONOR": "Honor",
        "SAMSNG": "Samsung",
        "INFINIX": "Infinix"
    }
    return brand_mapping.get(brand.upper(), brand)  # Default to the original brand if not in the mapping

def extract_product_data(product):
    """
    Extract relevant data from a product dictionary, including the category and stock status.
    """
    # Get current time in GMT+3
    current_time = datetime.utcnow() + timedelta(hours=3)
    current_time = current_time.replace(minute=0, second=0, microsecond=0)
    current_time_str = current_time.strftime('%Y-%m-%d %H:%M:%S')

    # Extract product details
    product_name = product.get('nameEn', 'No Name Available')
    product_parts = product_name.split(',', 1)  # Split the name into main and additional parts

    name = product_parts[0]  # The main product name
    additional_name = product_parts[1] if len(product_parts) > 1 else ''  # The rest of the product name

    processor_core = product.get('featureEnProcessorCore', 'No Processor Info Available')

    # Construct the specs field
    specs = f"{additional_name}, {processor_core}" if additional_name else processor_core

    product_link = product.get('productUrl', 'No URL Available')
    
    # Extract price details
    new_price = product.get('sellingPrice', None)
    old_price = product.get('wasPrice', new_price)
    
    in_stock_flag = product.get('inStockFlag', False)  # Default to False if missing
    stock = in_stock_flag  # No need to compare, it's already a boolean




    print(f"Product {product_name} - inStockFlag: {stock}, stock_status: {stock}")
    
    raw_brand = product.get('brand', ['No Brand Available'])[0]
    brand = format_brand(raw_brand)

    
    return {
        "name": name,
        "specs": specs,
        "new_price": new_price,
        "old_price": old_price,
        "brand": brand,
        "link": product_link,
        "category": 'tablets',
        "datetime": current_time_str,
        "stock": stock,
        "store": "extra"
    }

def fetch_products():
    """
    Fetch product data from the API with pagination.
    """
    start_index = 0
    all_products = []
    all_responses = []

    with requests.Session() as session:
        session.headers.update(HEADERS)

        while True:
            try:
                url = f"{API_URL}&start={start_index}"
                response = session.get(url)

                if response.status_code != 200:
                    print(f"Failed to fetch data at index {start_index}, status code: {response.status_code}")
                    break

                all_responses.append(response.json())

                data = response.json()
                products = data.get('response', {}).get('products', [])
                total_products = data.get('response', {}).get('numberOfProducts', 0)

                if not products:
                    print(f"No more products found at index {start_index}. Stopping.")
                    break

                for product_data in products:
                    all_products.append(extract_product_data(product_data))

                if len(all_products) >= total_products:
                    print(f"Fetched all {total_products} products.")
                    break

                start_index += 96

            except Exception as e:
                print(f"An error occurred at index {start_index}: {e}")
                time.sleep(5)

    if connection_pool:
        save_to_postgresql(all_products)

    save_to_csv(all_products)
    save_responses(all_responses)

def save_to_postgresql(products):
    """
    Save product data to PostgreSQL database using batch inserts.
    """
    if connection_pool is None:
        print("Skipping PostgreSQL save because the connection pool is not initialized.")
        return

    try:
        conn = connection_pool.getconn()
        cursor = conn.cursor()

        insert_query = f"""
        INSERT INTO {tablename} (name, specs, new_price, old_price, link, brand, category, datetime, stock, store)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        product_data = [
            (
                product['name'],
                product['specs'],
                product['new_price'],
                product['old_price'],
                product['link'],
                product['brand'],
                product['category'],
                product['datetime'],
                product['stock'],
                product["store"]
            )
            for product in products
        ]

        execute_batch(cursor, insert_query, product_data)
        conn.commit()
        connection_pool.putconn(conn)
        print(f"Successfully saved {len(products)} products to the PostgreSQL database.")

    except Exception as e:
        print(f"An error occurred while saving to the database: {e}")

def save_responses(responses):
    """
    Save all raw responses to a JSON file.
    """
    with open('raw_responses.json', 'w', encoding='utf-8') as json_file:
        json.dump(responses, json_file, ensure_ascii=False, indent=4)
    print(f"Saved {len(responses)} raw API responses to raw_responses.json.")

def save_to_csv(products):
    """
    Save product data to a CSV file.
    """
    try:
        csv_file = 'products.csv'
        fieldnames = ['name', 'specs', 'new_price', 'old_price', 'link', 'brand', 'category', 'datetime', 'stock', 'store']
        with open(csv_file, 'w', newline='', encoding='utf-8') as file:
            writer = csv.DictWriter(file, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(products)  # Write all products, no filtering
        print(f"Successfully saved {len(products)} products to {csv_file}.")
    except Exception as e:
        print(f"An error occurred while saving to CSV: {e}")


# Fetch products
fetch_products()

end_time = time.time()
elapsed_time = end_time - start_time
print(f"Total time taken: {elapsed_time:.2f} seconds.")
