// Tests basic support for function template declarations and definitions.
template <typename T>
T
//- @id defines AbsDecl
id(T x);

template <typename T>
T
//- @id defines AbsDefn
//- @id ucompletes AbsDecl
id(T x)
{ return x; }
//- AbsDecl.node/kind abs
//- AbsDefn.node/kind abs
//- Decl childof AbsDecl
//- Defn childof AbsDefn
//- Decl.node/kind function
//- Defn.node/kind function
//- Decl.complete incomplete
//- Defn.complete definition