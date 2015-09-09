
# tutils (Travis' Utils)

This is library of the utility code that I find myself re-using across my NLP
projects.  There are many things that I'm adding to this library, but the most
stable and useful component of `tutils` right now is the `Document` class, and
as such I will start with documentation for just this component. If you find
other parts useful, you are free to use them, but they are not necessarily
stable and are definitely note documentated.

**A point about *why*:**
If you have experience with NLP and you are used to tasks like POS tagging,
parsing, and NER tagging, you may be confused as to why you would spend much
time on the data structures for NLP. As far as representing the data for these
tasks, you can pretty much get away with running a simple tokenizer and storing
an array of strings representing the words in a sentence (perhaps with an extra
array of strings if you've run POS tagging first).
- sections (e.g. email headers, titles, captions, etc)
- semantic annotations: hierarchical NER, coref, relation extraction, SRL, semantic parsing, entity linking
- system combination: may want to be able to use more than one POS tagger for features
- when tokenization fails: some languages (e.g. Chinese) have ambiguous boundaries and some have rich morphology


#### `tutils` reads Concrete
[Concrete](https://github.com/hltcoe/concrete) is a data interchange format
(schema) designed to support easy collaboration and data serialization for NLP
researchers developed at the [JHU HLTCOE](http://hltcoe.jhu.edu/).  It is based
on [Apache Thrift](https://thrift.apache.org/), and thus comes with language
bindings in a variety of languages, including Java.  You might wonder why I'm
not using the Concrete Java classes directly; there are a few reasons.
- This is my own personal interface. Concrete was not always stable and it is
  nice to have an interface that I control which I can program against.
- Ergonomical reasons. For a variety of reasons ranging from how thrift works
  to Concrete's need to be able to represent multiple theories (versions of a
  type of annotation overtop of the same data), using Concrete directly can
  require a lot of boilerplate and indirection. I have sought to make this
  library a little easier to use and more efficient.
- This is a proving ground for things that may make it into Concrete.  I
  contribute to Concrete, but sometimes an idea/implementation needs to be
  validated before it is committed into a more conservative project like
  Concrete. This project lets me do that. For `Document` in particular though,
  I believe this is about as mature as Concrete is and would recommend anyone
  who doesn't need Concrete compliance to use `Document` instead.
- tutils is code, Concrete is data. Lisp aphorisms aside, there is a lot you
  can do with code that you can't do with just data.  This was a major design
  decision made by Concrete that `tutils` doesn't follow.  This decision was
  made so that all languages (at least those supported by thrift) were treated
  equally, but it limits what you can do because thrift only supports structs.
  `tutils` is a full blown java library.


#### `int`s over `String`s
NLP applications process text, meaning they have to deal with strings as the
primary input. Nearly all machine learning (ML) algorithms require integer
indexing (e.g. vector spaces for linear classifiers and inputs to neural nets).
The most common way to reconcile these two views of the data is usually called
an `Alphabet` (which is at least as old as
[mallet](http://mallet.cs.umass.edu/api/), but I suspect it is much older than
that), which is essentially a `Map<String,Integer>` and a `Map<Integer,String>`
(the latter of which is usually implemented as an `ArrayList<String>`).  Many
libraries will store `String`s and do the `int` lookup on the fly.  This is
slow since this operation often takes place in your tightest loop (feature
extraction), and can easily result in a 2-10x slowdown. `tutils` tries to
avoid this by pushing back this operation as early as possible (when a
`Document` is created) so that it is not done in performance-critical regions.
An `Alphabet` is kept in `Document` for things like debugging, but in general
all operations (including taking products of features) are designed to avoid
using `String`s as much as possible.

<!-- Strings *can* be very undesireable from a computational point
of view (e.g. things like checking equality, size to store, efficiency of
hashing, etc).  The simplest/most common case to keep in your mind is is
maintaining the numeric weights for String features (e.g.
`weight[word="thinking",pos="VBG"]=1.2`), which is commonly implemented as
either `HashMap<String,Double>` or a `HashMap<String,Integer>` and a
`double[]`.
We cannot hope to remove this data structure, but we need to carefully
control how often and when it is used.
`tutils` takes the approach of converting all 

The first problem with this approach is that it requires `HashMap` lookups
for basically all of the most common operations, which is slow.
-->

<!-- One common approach is to *intern* strings and use `HashMap`s all)
over the place. The problem with this approach is that it means that all of
your most common operations will still need to do a `HashMap` lookup every time
they want to do someting like compute a feature index or check inclusion in a
set. On its own this may seem like a low cost, but it can add up to a large
slowdown (well over 2x) in intensive ML algorithms for NLP.  The best way to
solve this is to use a perfect hash to represent strings, commonly implemented
with a class called an `Alphabet` (which is basically a `HashMap` with a
reverse `int` to `String` mapping).
-->

#### Bottom-up not top-down
When you have a hierarchy of entities (e.g. `Document > Section > Paragraph > Sentence > Token`),
it may seem natural to make these nested in an OOP or even relational DB sense.
`tutils` uses an approach that is related to the relational DB approach, but I'll
describe the downsides of doing this in an OOP way to highlight some pitfalls.
- If you are interested in leaves you have to do a lot of internal node boiler plate.
- You may need to create dummy internal nodes to represent an annotation.
- If annotations don't fully agree, it is not clear how to represent the data.
- When you have multiple theories, you double the depth of the tree and the number of indirections.



Other points (TODO):
- everything is a tree
- data structures stay the same, semantics of the data varies
- structure of arrays: the less data you use the more efficient

#### Eliminating the overhead introduced by Java/OOP
OOP language that support sub-typing (i.e. most if not all of them) add some
overhead for each object, and Java even more so due to things like how locking
is implemented.  For a wrapper around data, this is not really appropriate. You
don't need extensive polymorphism or abstraction with your data: you just need the
data. As such, data in `tutils` is primarily stored as primitive arrays.  I
have includeded convenience classes like `Document.Token` and
`Document.Constituent` for cases where the conveience is worth the overhead of
using a class, but you can use `Document` without these, where each method is
not much more than an array access which can be easily inlined.

#### Structure of arrays (over array of structures)
This is a common convention in game programming and other high-performance
computing areas. It is usually more efficient to store `{X[],Y[],Z[]}` than
`{X,Y,Z}[]`. This has to do with memory bandwidth, data locality, and
alignment.  The easiest way to see this effect in action is to imagine a very
fat struct (e.g. `Token`, which needs to include `word`, `posTool1`,
`posTool2`, `posGold`, `nerTool1`, `lemma`, `brownCluster`, `superSense`,
`language`, `wordShape`, etc) and use it in a piece of code which only reads
one of these fields (e.g. `contains(word)` or `matches(simpleNP)` or
`hist(language)`).  If you have an array of structs, you need to read every
field into memory even though you are only going to use one of those fields.
This means more memory bandwidth is required and more cache evictions will take
place, which will lead to slower code (I will not get into the SIMD/alignment
argument for structure of arrays because I don't think it is relevant to Java).
The take-home message is that if you use structures of arrays (SoA), you can
get speedups according to what fraction of the data members your code uses: the
less data read the faster the code.  While this may sound like wishful thinking
and/or premature optimization, even very [simple
benchmark](https://gist.github.com/twolfe18/8168262c5420c7a62d39) can verify
that this phenomenon is real and affects JVM programs.



#### Why *Java*?
You may look at my code an wonder why I landed on Java.  1) I know it well, 2)
the JVM is great, 3) the tooling is great, 4) my employer likes it, and 5) some
other folks in NLP use Java a lot.  If you hate Java a lot, you could probably
write a regular expression converting `Document` from Java to C/C++/C#.


