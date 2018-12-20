package codex.model;

/**
 * Available access levels for models' properties.
 * @see AbstractModel#getProperties
 * @see AbstractModel#addProperty
 * @author Gredyaev Ivan
 */
public enum Access {
    Any,
    Edit,
    Select
}
