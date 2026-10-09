# ne_land.bin — береговые линии для debug-прототипа глобуса (V21)

## Источник

- **Natural Earth**, набор `ne_50m_land` (1:50m land polygons), GeoJSON из
  репозитория https://github.com/nvkelso/natural-earth-vector
  (`geojson/ne_50m_land.geojson`).
- Лицензия: **public domain** — https://www.naturalearthdata.com/about/terms-of-use/
  («All versions of Natural Earth raster + vector map data found on this website
  are in the public domain»). Attribution не обязателен, но приветствуется.
- Исходный GeoJSON (1.6 МБ) в Git **не коммитится**; скачивается вручную.

## Генерация

```sh
python3 app/src/debug/tools/prepare_land.py ne_50m_land.geojson \
    app/src/debug/assets/ne_land.bin 0.10
```

Пайплайн: отброшены микро-острова (< 0.35° охвата), упрощение
Дугласа–Пекера с допуском 0.10°, квантование 16 бит, дельта-кодирование
zigzag-varint. Формат описан в заголовке `prepare_land.py`.
Итог: 616 полигонов, 11 031 точка, 36.3 КБ.

Данные используются только debug-сборкой (`app/src/debug/`), в release не входят.
