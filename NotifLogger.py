#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NotifLogger Token Generator (SQLite + Console)
Исправленная версия: совместима с Android приложением, использует Device ID (ANDROID_ID),
генерирует токен с полями device_id, start_date, duration_seconds
"""
import sqlite3
import base64
import json
from datetime import datetime, timedelta, timezone

DB_FILE = "tokens.db"

def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            start_date TEXT NOT NULL,
            duration_seconds INTEGER NOT NULL,
            token TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
    """)
    conn.commit()
    conn.close()

def generate_token(device_id: str, duration_minutes: int):
    # Проверка уникальности Device ID
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("SELECT device_id FROM tokens WHERE device_id = ?", (device_id,))
    if c.fetchone():
        conn.close()
        raise ValueError("Device ID уже используется!")
    
    # Время старта = текущее время UTC с буфером в 30 секунд
    start_utc = datetime.now(timezone.utc) + timedelta(seconds=30)
    duration_seconds = duration_minutes * 60
    end_utc = start_utc + timedelta(seconds=duration_seconds)
    
    # Формат даты с 'Z'
    start_date_str = start_utc.strftime("%Y-%m-%dT%H:%M:%SZ")
    
    # Формируем данные токена
    token_data = {
        "device_id": device_id,
        "start_date": start_date_str,
        "duration_seconds": duration_seconds
    }
    
    json_data = json.dumps(token_data, separators=(",", ":"))
    token_base64 = base64.urlsafe_b64encode(json_data.encode()).decode()
    
    # Сохраняем в SQLite
    c.execute("""
        INSERT INTO tokens (device_id, start_date, duration_seconds, token, created_at)
        VALUES (?, ?, ?, ?, ?)
    """, (
        device_id,
        start_date_str,
        duration_seconds,
        token_base64,
        datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    ))
    conn.commit()
    conn.close()
    
    return token_base64, start_utc, end_utc

def main():
    init_db()
    
    device_id = input("Введите Device ID (ANDROID_ID): ").strip()
    if not device_id:
        print("❌ Ошибка: Device ID не может быть пустым!")
        return
    
    try:
        duration = int(input("Введите период действия (в минутах): ").strip())
        if duration <= 0:
            print("❌ Ошибка: период должен быть положительным числом!")
            return
    except ValueError:
        print("❌ Ошибка: период должен быть числом!")
        return
    
    try:
        token, start_utc, end_utc = generate_token(device_id, duration)
        
        print("\n" + "="*50)
        print("✅ ТОКЕН УСПЕШНО СГЕНЕРИРОВАН!")
        print("="*50)
        print(f"Device ID:  {device_id}")
        print(f"Начало UTC: {start_utc.strftime('%d.%m.%Y %H:%M:%S')}")
        print(f"Конец UTC:  {end_utc.strftime('%d.%m.%Y %H:%M:%S')}")
        print(f"Период:     {duration} минут ({duration * 60} секунд)")
        print("="*50)
        print("ТОКЕН ДЛЯ ВСТАВКИ В ПРИЛОЖЕНИЕ:")
        print(token)
        print("="*50)
        
        # Отладочная информация
        print(f"\nДля отладки - декодированный JSON:")
        try:
            decoded = base64.urlsafe_b64decode(token.encode()).decode()
            print(decoded)
        except Exception as e:
            print(f"Ошибка декодирования: {e}")
            
    except ValueError as e:
        print(f"❌ Ошибка: {e}")

if __name__ == "__main__":
    main()