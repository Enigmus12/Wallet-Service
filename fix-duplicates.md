# Solución: Eliminar Wallets Duplicadas

## Problema
El error `returned non unique result` indica que hay múltiples wallets con el mismo `userId` en MongoDB.

## Solución

### Opción 1: Limpiar toda la colección (MÁS RÁPIDO - úsala si es desarrollo)

Conecta a MongoDB y ejecuta:

```javascript
use wallet_service  // o tu nombre de base de datos

// Ver duplicados
db.wallets.aggregate([
  { $group: { _id: "$userId", count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
])

// ELIMINAR TODA LA COLECCIÓN (solo en desarrollo)
db.wallets.drop()

// Recrear índice único
db.wallets.createIndex({ "userId": 1 }, { unique: true })
```

### Opción 2: Eliminar solo duplicados (conserva datos)

```javascript
use wallet_service

// Ver todos los duplicados primero
db.wallets.aggregate([
  { $group: { 
      _id: "$userId", 
      ids: { $push: "$_id" },
      count: { $sum: 1 } 
  }},
  { $match: { count: { $gt: 1 } } }
])

// Para cada userId duplicado, eliminar todos menos el primero
db.wallets.aggregate([
  { $group: { 
      _id: "$userId", 
      ids: { $push: "$_id" }
  }},
  { $match: { "ids.1": { $exists: true } } }
]).forEach(function(doc) {
  var idsToRemove = doc.ids.slice(1);  // Todos menos el primero
  db.wallets.deleteMany({ _id: { $in: idsToRemove } });
  print("Eliminados duplicados de userId: " + doc._id);
});

// Recrear índice único
db.wallets.createIndex({ "userId": 1 }, { unique: true })
```

### Opción 3: Desde la aplicación (Spring Boot)

Reinicia la app con esta configuración temporal en `application.properties`:

```properties
# Forzar recreación de índices
spring.data.mongodb.auto-index-creation=true
```

Luego elimina duplicados manualmente desde MongoDB o usa la Opción 1.

## Después de limpiar

1. Reinicia la aplicación
2. Prueba la compra de nuevo
3. El índice único impedirá futuros duplicados

## Verificar que funcionó

```javascript
// Ver índices
db.wallets.getIndexes()

// Debe mostrar:
// { "v": 2, "key": { "userId": 1 }, "name": "userId_1", "unique": true }
```
